package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

final class FailingHttpClient extends HttpClientDouble {

    private final Exception failure;
    private final boolean throwsWhenSending;
    private final @Nullable HttpClient afterTheFirstFailure;
    private final AtomicInteger attempts = new AtomicInteger();

    private FailingHttpClient(Exception failure, boolean throwsWhenSending, @Nullable HttpClient afterTheFirstFailure) {
        this.failure = failure;
        this.throwsWhenSending = throwsWhenSending;
        this.afterTheFirstFailure = afterTheFirstFailure;
    }

    static FailingHttpClient failingWith(Exception failure) {
        return new FailingHttpClient(failure, false, null);
    }

    static FailingHttpClient throwingWhenSending(RuntimeException failure) {
        return new FailingHttpClient(failure, true, null);
    }

    static FailingHttpClient failingOnceWith(Exception failure, HttpClient afterwards) {
        return new FailingHttpClient(failure, false, afterwards);
    }

    int attempts() {
        return attempts.get();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        if (attempts.getAndIncrement() > 0 && afterTheFirstFailure != null) {
            return afterTheFirstFailure.sendAsync(request, handler);
        }
        if (throwsWhenSending) {
            throw (RuntimeException) failure;
        }
        return CompletableFuture.failedFuture(failure);
    }
}
