package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A request to the Fio API failed. The subclasses name the documented refusals, an answer the client does not
 * understand and a request that got no answer; this class itself is thrown for any other HTTP status.
 *
 * <p>This class itself is also thrown, without a status, for an answer longer than the client reads (64 MiB).
 * Like {@link FioMalformedResponseException}, it comes after the bank received the request: a submitted batch may
 * have been accepted, and {@link FioClient.Transactions#sinceBookmark()} may have moved the bookmark.
 *
 * <p>The hierarchy is sealed, so a {@code switch} over it can be exhaustive. Every message and body has the token
 * masked, and a body is cut to its first {@value #RESPONSE_BODY_LIMIT} characters. The status and the body are
 * {@link OptionalInt} and {@link Optional} rather than nullable, as an exception is read far from where it is
 * thrown.
 */
public sealed class FioException extends RuntimeException
        permits FioRateLimitException, FioAuthorizationException, FioHistoryLockedException,
        FioTooManyTransactionsException, FioMalformedResponseException, FioTransportException {

    /** The most characters of a response body an exception keeps. */
    public static final int RESPONSE_BODY_LIMIT = 16 * 1024;

    private static final String CUT_MARK = "\u2026";

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
        this.responseBody = cut(responseBody);
    }

    /**
     * Creates an exception for a request that got no answer.
     *
     * @param message what happened, with the token masked
     * @param cause the cause, with the token masked
     */
    public FioException(String message, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = null;
        this.responseBody = null;
    }

    private static @Nullable String cut(@Nullable String body) {
        return body == null || body.length() <= RESPONSE_BODY_LIMIT ? body : body.substring(0, RESPONSE_BODY_LIMIT) + CUT_MARK;
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
     * Returns the body of the answer with the token masked and cut to its first {@value #RESPONSE_BODY_LIMIT}
     * characters, with an ellipsis after a cut; empty when there was no answer.
     *
     * @return the body
     */
    public Optional<String> responseBody() {
        return Optional.ofNullable(responseBody);
    }
}
