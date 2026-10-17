package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

/**
 * The other side of a movement, as far as the bank identifies it. Every part is {@code null} when the bank did not
 * send it; a fee or interest has no counterparty at all.
 *
 * @param account the account of the counterparty
 * @param name the name of the account holder
 * @param bankName the name of the counterparty's bank
 * @param bic the BIC of the counterparty's bank
 */
public record Counterparty(
        @Nullable BankAccount account,
        @Nullable String name,
        @Nullable String bankName,
        @Nullable String bic) {

    private static final Counterparty NONE = new Counterparty(null, null, null, null);

    /**
     * Returns the counterparty of a movement that has none.
     *
     * @return a counterparty with every part {@code null}
     */
    public static Counterparty none() {
        return NONE;
    }

    /**
     * Tells whether the bank sent nothing about the counterparty.
     *
     * @return whether every part is {@code null}
     */
    public boolean isEmpty() {
        return account == null && name == null && bankName == null && bic == null;
    }
}
