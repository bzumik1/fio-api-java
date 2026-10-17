package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * The token does not exist, has expired or is not active yet. Fio's documentation names HTTP 500 for this; the
 * client treats 401 and 403 the same way.
 */
public final class FioAuthorizationException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what was refused, with the token masked
     * @param statusCode the HTTP status of the refusal
     * @param responseBody the body of the refusal, with the token masked
     */
    public FioAuthorizationException(String message, int statusCode, @Nullable String responseBody) {
        super(message, statusCode, responseBody);
    }
}
