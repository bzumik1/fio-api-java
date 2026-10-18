package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * One payment order in a {@link PaymentBatch}: a payment within the Czech Republic, a SEPA payment in euro or a
 * payment abroad outside SEPA.
 */
public sealed interface PaymentOrder permits DomesticPayment, EuroPayment, ForeignPayment {

    /**
     * Returns the Fio account the order is submitted for: the one the money leaves, or, for a
     * {@link DomesticPayment.Type#DIRECT_DEBIT direct debit}, the one it is collected to. It is the account the
     * token belongs to, so every order of a {@link PaymentBatch} is from it and in its currency: a SEPA payment
     * needs a euro account, and with it its own token and batch.
     *
     * @return the account of the account holder submitting the order
     */
    DomesticAccount from();

    /**
     * Returns the counterparty's account: the one the money goes to, or, for a
     * {@link DomesticPayment.Type#DIRECT_DEBIT direct debit}, the one it is collected from.
     *
     * @return the counterparty's account
     */
    BankAccount to();

    /**
     * Returns the amount; its currency has to be the currency of the {@link #from() account holder's account}.
     *
     * @return the amount
     */
    Money amount();

    /**
     * Returns the day the payment is due.
     *
     * @return the due date
     */
    LocalDate date();

    /**
     * Returns the payer's own label of the payment, which the recipient never sees.
     *
     * @return the comment
     */
    @Nullable String comment();
}
