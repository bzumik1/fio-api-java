package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * The bank refused a download with HTTP 413 because the answer would hold more than {@link #LIMIT} movements.
 * Ask for a shorter period, or move the bookmark closer to the present.
 */
public final class FioTooManyTransactionsException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The most movements one download may hold. */
    public static final int LIMIT = 50_000;

    private static final int PAYLOAD_TOO_LARGE = 413;

    /**
     * Creates the exception.
     *
     * @param message what was refused, with the token masked
     * @param responseBody the body of the refusal, with the token masked
     */
    public FioTooManyTransactionsException(String message, @Nullable String responseBody) {
        super(message, PAYLOAD_TOO_LARGE, responseBody);
    }
}
