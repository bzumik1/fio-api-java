package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * The bank did not answer: the connection was refused, the request timed out or the thread was interrupted. There
 * is no status code and no body.
 */
public final class FioTransportException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what failed, with the token masked
     * @param cause the cause, with the token masked
     */
    public FioTransportException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
