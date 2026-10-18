package io.github.bzumik1.fio;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class RecordingHttpClient extends HttpClientDouble {

    private final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicLong delivered = new AtomicLong();
    private final int status;
    private final Map<String, List<String>> headers;
    private final List<byte[]> parts;

    RecordingHttpClient(int status, byte[] body) {
        this(status, Map.of(), body.length == 0 ? List.of() : List.of(body.clone()));
    }

    private RecordingHttpClient(int status, Map<String, List<String>> headers, List<byte[]> parts) {
        this.status = status;
        this.headers = Map.copyOf(headers);
        this.parts = List.copyOf(parts);
    }

    static RecordingHttpClient answeringInParts(int status, List<byte[]> parts) {
        return new RecordingHttpClient(status, Map.of(), parts);
    }

    static RecordingHttpClient answeringWithContentLength(int status, long contentLength, byte[] body) {
        return new RecordingHttpClient(status, Map.of("Content-Length", List.of(Long.toString(contentLength))), List.of(body.clone()));
    }

    long deliveredBytes() {
        return delivered.get();
    }

    List<HttpRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        requests.add(request);
        HttpHeaders headers = HttpHeaders.of(this.headers, (name, value) -> true);
        HttpResponse.BodySubscriber<T> subscriber = handler.apply(new ResponseInfo(status, headers));
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });
        for (byte[] part : parts) {
            if (cancelled.get()) {
                break;
            }
            delivered.addAndGet(part.length);
            subscriber.onNext(List.of(ByteBuffer.wrap(part)));
        }
        if (!cancelled.get()) {
            subscriber.onComplete();
        }
        return subscriber.getBody().toCompletableFuture().thenApply(content -> new Response<>(request, status, headers, content));
    }

    private record ResponseInfo(int statusCode, HttpHeaders headers) implements HttpResponse.ResponseInfo {

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private record Response<T>(HttpRequest request, int statusCode, HttpHeaders headers, T body) implements HttpResponse<T> {

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
