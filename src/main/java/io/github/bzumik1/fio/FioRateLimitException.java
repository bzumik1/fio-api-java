package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * The token was used less than 30 seconds ago, so the request was refused with HTTP 409 or, under
 * {@link RateLimit#failFast()}, not sent at all. {@link #retryAfter()} says when the next request may go.
 */
public final class FioRateLimitException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int CONFLICT = 409;

    /** @serial when the next request may go */
    private final Instant retryAfter;

    /**
     * Creates the exception.
     *
     * @param message what was refused, with the token masked
     * @param responseBody the body of the refusal with the token masked, or {@code null} when nothing was sent
     * @param retryAfter when the next request may go
     */
    public FioRateLimitException(String message, @Nullable String responseBody, Instant retryAfter) {
        super(message, CONFLICT, responseBody);
        this.retryAfter = requireNonNull(retryAfter, "retryAfter");
    }

    /**
     * Returns when the next request with the token may go.
     *
     * @return the time of the next slot
     */
    public Instant retryAfter() {
        return retryAfter;
    }
}
