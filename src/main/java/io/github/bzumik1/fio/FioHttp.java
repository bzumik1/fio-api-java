package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static io.github.bzumik1.fio.FioClient.MINIMUM_REQUEST_INTERVAL;
import static java.util.Objects.requireNonNull;

final class FioHttp {

    static final Set<String> MESSAGE_LANGUAGES = Set.of("cs", "sk", "en");
    static final int RESPONSE_SIZE_LIMIT = 64 * 1024 * 1024;

    private final FioToken token;
    private final String baseUri;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Duration requestTimeout;
    private final RequestPacer pacer;
    private final Locale messageLocale;
    private final int responseSizeLimit;

    FioHttp(FioToken token, URI baseUri, HttpClient httpClient, Clock clock, Duration requestTimeout,
            RequestPacer pacer, Locale messageLocale, int responseSizeLimit) {
        this.token = token;
        this.baseUri = baseUri.toString().replaceAll("/+$", "");
        this.httpClient = httpClient;
        this.clock = clock;
        this.requestTimeout = requestTimeout;
        this.pacer = pacer;
        this.messageLocale = messageLocale;
        this.responseSizeLimit = responseSizeLimit;
    }

    FioToken token() {
        return token;
    }

    String messageLanguage() {
        return messageLocale.getLanguage();
    }

    FioResponse get(String accept, String... pathSegments) {
        HttpRequest request = HttpRequest.newBuilder(uri(pathSegments))
                .timeout(requestTimeout)
                .header("Accept", accept)
                .GET()
                .build();
        return send(request);
    }

    FioResponse postMultipart(String pathSegment, Map<String, String> fields, String fileName, byte[] file) {
        String boundary = "fio-api-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(uri(pathSegment, ""))
                .timeout(requestTimeout)
                .header("Accept", "application/xml")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(multipart(boundary, fields, fileName, file)))
                .build();
        return send(request);
    }

    <T> T parsed(FioResponse response, Function<byte[], T> parser) {
        try {
            return parser.apply(response.body());
        } catch (MalformedResponseException e) {
            throw new FioMalformedResponseException(
                    response.description() + " was answered with HTTP " + response.status()
                            + " and a body this client does not understand: " + token.mask(e.getMessage()),
                    response.status(),
                    token.mask(response.text()),
                    TokenMaskedFailure.withoutToken(e, token));
        }
    }

    private FioResponse send(HttpRequest request) {
        return pacer.paced(() -> exchange(request), () -> describe(request));
    }

    private FioResponse exchange(HttpRequest request) {
        CompletableFuture<HttpResponse<byte[]>> pending = sendAsync(request);
        HttpResponse<byte[]> response;
        try {
            response = pending.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw failed(request, e.getCause());
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new FioTransportException(
                    describe(request) + " did not finish within " + requestTimeout,
                    TokenMaskedFailure.withoutToken(e, token),
                    true);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new FioTransportException(
                    describe(request) + " was interrupted", TokenMaskedFailure.withoutToken(e, token), true);
        }
        FioResponse answer = new FioResponse(
                describe(request),
                response.statusCode(),
                response.headers().firstValue("Content-Type").orElse(null),
                response.body());
        if (answer.status() / 100 != 2) {
            throw failure(answer);
        }
        return answer;
    }

    private CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest request) {
        try {
            return httpClient.sendAsync(request, info -> new BoundedBody(responseSizeLimit));
        } catch (RuntimeException e) {
            throw failed(request, e);
        }
    }

    private FioException failed(HttpRequest request, Throwable cause) {
        if (cause instanceof ResponseTooLargeException) {
            return new FioException(describe(request) + " was answered with more than " + responseSizeLimit
                    + " bytes, which this client does not read", TokenMaskedFailure.withoutToken(cause, token));
        }
        return new FioTransportException(
                describe(request) + " failed: " + token.mask(cause.toString()),
                TokenMaskedFailure.withoutToken(cause, token),
                !(cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException));
    }

    private FioException failure(FioResponse response) {
        int status = response.status();
        String body = token.mask(response.text());
        String refused = response.description() + " was refused with HTTP " + status;
        return switch (status) {
            case 409 -> new FioRateLimitException(
                    refused + ": the token was already used less than " + MINIMUM_REQUEST_INTERVAL.toSeconds() + " seconds ago",
                    body,
                    clock.instant().plus(MINIMUM_REQUEST_INTERVAL));
            case 401, 403, 500 -> new FioAuthorizationException(
                    refused + ": the bank refused the token (it does not exist, has expired or is not active), or"
                            + (status == 500 ? " the bank failed or could not read the request" : " the account is not allowed"),
                    status,
                    body);
            case 413 -> new FioTooManyTransactionsException(
                    refused + ": the answer would hold more than " + FioTooManyTransactionsException.LIMIT
                            + " movements; ask for a shorter period",
                    body);
            case 422 -> new FioHistoryLockedException(
                    refused + ": movements older than 90 days need the full history unlocked in Fio internet banking",
                    body);
            default -> new FioException(refused, status, body);
        };
    }

    private String describe(HttpRequest request) {
        return "Fio API request " + request.method() + " " + token.mask(request.uri().toString());
    }

    private URI uri(String... segments) {
        return URI.create(baseUri + "/" + String.join("/", segments));
    }

    private static List<byte[]> multipart(String boundary, Map<String, String> fields, String fileName, byte[] file) {
        List<byte[]> parts = new ArrayList<>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            parts.add(utf8("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + field.getValue() + "\r\n"));
        }
        parts.add(utf8("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n"));
        parts.add(file.clone());
        parts.add(utf8("\r\n--" + boundary + "--\r\n"));
        return parts;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final HttpResponse.BodySubscriber<byte[]> collector = HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;
        private long received;
        private Flow.@Nullable Subscription subscription;

        private BoundedBody(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletableFuture<byte[]> getBody() {
            return collector.getBody().toCompletableFuture();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            collector.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                received += buffer.remaining();
            }
            if (received > limit) {
                requireNonNull(subscription, "subscription").cancel();
                collector.onError(new ResponseTooLargeException(limit));
                return;
            }
            collector.onNext(buffers);
        }

        @Override
        public void onError(Throwable throwable) {
            collector.onError(throwable);
        }

        @Override
        public void onComplete() {
            collector.onComplete();
        }
    }

    static final class ResponseTooLargeException extends RuntimeException {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private ResponseTooLargeException(int limit) {
            super("The answer is longer than " + limit + " bytes");
        }
    }

    record FioResponse(String description, int status, @Nullable String mediaType, byte[] body) {

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        Download document(Charset documentedCharset) {
            return Download.of(mediaType == null ? "application/octet-stream" : mediaType, documentedCharset, body);
        }
    }
}
