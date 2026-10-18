package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * The bank answered with more bytes than the client reads (256 MiB unless
 * {@link FioClient.Builder#maximumResponseSize(int)} says otherwise), so the answer was not read. There is no
 * status code and no body.
 *
 * <p>The bank received the request and acted on it before it answered, so {@link #mayHaveTakenEffect()} is
 * {@code true}: a submitted batch may have been accepted, and {@link FioClient.Transactions#sinceBookmark()} may
 * have moved the bookmark. A download by period or number can be asked for a shorter period, or with a larger
 * limit.
 */
public final class FioResponseTooLargeException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** @serial the most bytes of an answer the client reads */
    private final int limit;

    /**
     * Creates the exception.
     *
     * @param message what was not read, with the token masked
     * @param limit the most bytes of an answer the client reads
     * @param cause the cause, with the token masked
     */
    public FioResponseTooLargeException(String message, int limit, @Nullable Throwable cause) {
        super(message, cause);
        this.limit = limit;
    }

    /**
     * Returns the most bytes of an answer the client reads, which this answer exceeded.
     *
     * @return the limit in bytes
     */
    public int limit() {
        return limit;
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
