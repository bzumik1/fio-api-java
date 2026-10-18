package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * The bank answered with a success status, but the body is not what this client expects: not the documented
 * JSON, XML or statement number, or a value in it that does not fit. The cause names what did not fit, and
 * {@link #responseBody()} holds the beginning of the body, so the answer can be reported.
 *
 * <p>The bank acted on the request before it answered, so {@link #mayHaveTakenEffect()} is {@code true}: check
 * the pending batches in Fio internet banking, or move the bookmark back, before trying again. A download by period
 * or number can simply be asked again, and most likely gets the same answer.
 */
public final class FioMalformedResponseException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what was not understood, with the token masked
     * @param statusCode the HTTP status of the answer
     * @param responseBody the beginning of the body, with the token masked
     * @param cause what did not fit, with the token masked
     */
    public FioMalformedResponseException(String message, int statusCode, @Nullable String responseBody, @Nullable Throwable cause) {
        super(message, statusCode, responseBody, cause);
    }

    /**
     * Returns {@code true}: the bank received the request and answered.
     *
     * @return {@code true}
     */
    @Override
    public boolean mayHaveTakenEffect() {
        return true;
    }
}
