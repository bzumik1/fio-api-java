package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * The bank did not answer: the connection was refused, the request timed out or the thread was interrupted. There
 * is no status code and no body.
 *
 * <p>{@link #mayHaveReachedTheBank()} tells whether the request may have gone out before the failure. When it may
 * have, the bank may have acted on it: a payment batch may be waiting for authorisation, or the bookmark may have
 * moved. Such a request is not to be repeated blindly; check in Fio internet banking first.
 *
 * <p>The cause is the JDK's exception when its text is free of the token; when the token appears anywhere in the
 * cause chain, the chain is replaced by copies that keep the masked messages but not the classes.
 */
public final class FioTransportException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** @serial whether the request may have reached the bank */
    private final boolean mayHaveReachedTheBank;

    /**
     * Creates the exception.
     *
     * @param message what failed, with the token masked
     * @param cause the cause, with the token masked
     * @param mayHaveReachedTheBank whether the request may have gone out before the failure
     */
    public FioTransportException(String message, @Nullable Throwable cause, boolean mayHaveReachedTheBank) {
        super(message, cause);
        this.mayHaveReachedTheBank = mayHaveReachedTheBank;
    }

    /**
     * Tells whether the request may have reached the bank before the failure. {@code false} means it certainly did
     * not: the connection could not be made, or the client gave up before sending. {@code true} means the outcome
     * is unknown: the request may have been sent and acted on, so a payment batch or a bookmark move is not to be
     * repeated before checking in Fio internet banking.
     *
     * @return whether the outcome of the request is unknown
     */
    public boolean mayHaveReachedTheBank() {
        return mayHaveReachedTheBank;
    }
}
