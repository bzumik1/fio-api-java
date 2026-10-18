package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.util.Currency;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The bank's answer to a submitted batch of payment orders. A batch the bank accepted still has to be authorised
 * by a person in Fio internet banking.
 *
 * @param errorCode 0 when the batch was accepted, 2 when accepted with warnings, else the code Fio's documentation
 *        lists
 * @param status whether the batch was accepted, accepted with warnings, or refused
 * @param batchId the id the bank gave the batch (číslo dávky), when it was accepted
 * @param sums the sums of the orders, one for each currency of the batch
 * @param messages what the bank said about the batch and its orders, in the language chosen for the client
 */
public record ImportResult(
        int errorCode,
        Status status,
        @Nullable Long batchId,
        List<Sum> sums,
        List<Message> messages) {

    /**
     * Validates the components and copies the sums and the messages.
     *
     * @param errorCode the error code
     * @param status the status
     * @param batchId the batch id
     * @param sums the sums
     * @param messages the messages
     */
    public ImportResult {
        requireNonNull(status, "status");
        sums = List.copyOf(sums);
        messages = List.copyOf(messages);
    }

    /**
     * Tells whether the bank accepted the batch, with or without warnings.
     *
     * @return whether the orders wait for authorisation
     */
    public boolean isAccepted() {
        return status == Status.OK || status == Status.WARNING;
    }

    /**
     * The outcome of a batch.
     *
     * <p>Constants may be added in a minor release when Fio answers with a new status, so a {@code switch} over
     * this type needs a {@code default} branch.
     */
    public enum Status {

        /** Every order was accepted. */
        OK,
        /** The orders were accepted, but some values did not match; see the messages. */
        WARNING,
        /** An order was wrong, and no order of the batch was accepted. */
        ERROR,
        /** The bank's system failed, and no order of the batch was accepted. */
        FATAL
    }

    /**
     * The sums of the orders of a batch in one currency. The sums compare by value, like every {@link Money}.
     *
     * @param currency the currency the bank names the sum by
     * @param debit the sum of the debit orders in that currency, or {@code null} when the bank left it out
     * @param credit the sum of the credit orders in that currency, or {@code null} when the bank left it out
     */
    public record Sum(Currency currency, @Nullable Money debit, @Nullable Money credit) {

        /**
         * Validates the currency.
         *
         * @param currency the currency
         * @param debit the sum of the debit orders
         * @param credit the sum of the credit orders
         */
        public Sum {
            requireNonNull(currency, "currency");
        }
    }

    /**
     * What the bank said about the batch or about one order of it.
     *
     * @param orderNumber the position of the order in the submitted batch (for an uploaded file, in the file),
     *        from 1, or {@code null} when the message is about the whole batch or when the bank's numbering could
     *        not be mapped back to the batch, in which case every message of the answer has none
     * @param status the status of that order, or {@code null} when the bank gave none
     * @param text the message
     */
    public record Message(@Nullable Integer orderNumber, @Nullable Status status, String text) {

        /**
         * Validates the text.
         *
         * @param orderNumber the order number
         * @param status the status
         * @param text the message
         */
        public Message {
            requireNonNull(text, "text");
        }
    }
}
