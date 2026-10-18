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

final class RecordingHttpClient extends HttpClientDouble {

    private final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
    private final int status;
    private final byte[] body;

    RecordingHttpClient(int status, byte[] body) {
        this.status = status;
        this.body = body.clone();
    }

    List<HttpRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        requests.add(request);
        HttpHeaders headers = HttpHeaders.of(Map.of(), (name, value) -> true);
        HttpResponse.BodySubscriber<T> subscriber = handler.apply(new ResponseInfo(status, headers));
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        if (body.length > 0) {
            subscriber.onNext(List.of(ByteBuffer.wrap(body)));
        }
        subscriber.onComplete();
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
