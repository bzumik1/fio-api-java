package io.github.bzumik1.fio;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

final class FailingHttpClient extends HttpClientDouble {

    private final Exception failure;
    private final boolean throwsWhenSending;

    private FailingHttpClient(Exception failure, boolean throwsWhenSending) {
        this.failure = failure;
        this.throwsWhenSending = throwsWhenSending;
    }

    static FailingHttpClient failingWith(Exception failure) {
        return new FailingHttpClient(failure, false);
    }

    static FailingHttpClient throwingWhenSending(RuntimeException failure) {
        return new FailingHttpClient(failure, true);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        if (throwsWhenSending) {
            throw (RuntimeException) failure;
        }
        return CompletableFuture.failedFuture(failure);
    }
}
