package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A request to the Fio API failed. The subclasses name the documented refusals; this class itself is thrown for
 * any other status, and for an answer the client does not understand.
 *
 * <p>The hierarchy is sealed, so a {@code switch} over it can be exhaustive. Every message and body has the token
 * masked.
 */
public sealed class FioException extends RuntimeException
        permits FioRateLimitException, FioAuthorizationException, FioHistoryLockedException,
        FioTooManyTransactionsException, FioTransportException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** @serial the HTTP status of the answer, or {@code null} when there was none */
    private final @Nullable Integer statusCode;

    /** @serial the body of the answer with the token masked, or {@code null} when there was none */
    private final @Nullable String responseBody;

    /**
     * Creates an exception for a refused request.
     *
     * @param message what was refused, with the token masked
     * @param statusCode the HTTP status of the answer
     * @param responseBody the body of the answer, with the token masked
     */
    public FioException(String message, int statusCode, @Nullable String responseBody) {
        this(message, statusCode, responseBody, null);
    }

    /**
     * Creates an exception for a refused request, with its cause.
     *
     * @param message what was refused, with the token masked
     * @param statusCode the HTTP status of the answer
     * @param responseBody the body of the answer, with the token masked
     * @param cause the cause, with the token masked
     */
    public FioException(String message, int statusCode, @Nullable String responseBody, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    FioException(String message, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = null;
        this.responseBody = null;
    }

    /**
     * Returns the HTTP status of the answer; empty when there was no answer.
     *
     * @return the status
     */
    public OptionalInt statusCode() {
        return statusCode == null ? OptionalInt.empty() : OptionalInt.of(statusCode);
    }

    /**
     * Returns the body of the answer with the token masked; empty when there was no answer.
     *
     * @return the body
     */
    public Optional<String> responseBody() {
        return Optional.ofNullable(responseBody);
    }
}
