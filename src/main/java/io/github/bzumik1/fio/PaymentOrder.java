package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * One payment order in a {@link PaymentBatch}: a payment within the Czech Republic, a SEPA payment in euro or a
 * payment abroad outside SEPA.
 */
public sealed interface PaymentOrder permits DomesticPayment, EuroPayment, ForeignPayment {

    /**
     * Returns the account the payment leaves; its currency is the currency of the order.
     *
     * @return the payer's account
     */
    DomesticAccount from();

    /**
     * Returns the amount; its currency has to be the currency of the payer's account.
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
