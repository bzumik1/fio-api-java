package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * The request asked for movements older than 90 days, which the bank gives only for 10 minutes after the account
 * holder unlocks the full history for the token in Fio internet banking (Nastavení, API, the lock icon). The bank
 * answers HTTP 422; the body, in Czech, names the earliest day available without unlocking.
 */
public final class FioHistoryLockedException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int UNPROCESSABLE_ENTITY = 422;

    /**
     * Creates the exception.
     *
     * @param message what was refused, with the token masked
     * @param responseBody the body of the refusal, with the token masked
     */
    public FioHistoryLockedException(String message, @Nullable String responseBody) {
        super(message, UNPROCESSABLE_ENTITY, responseBody);
    }
}
