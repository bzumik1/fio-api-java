package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The bank's answer to a submitted batch of payment orders. A batch the bank accepted still has to be authorised
 * by a person in Fio internet banking.
 *
 * @param errorCode 0 when the batch was accepted, else the code Fio's documentation lists
 * @param status whether the batch was accepted, accepted with warnings, or refused
 * @param instructionId the id of the batch in the bank, when it was accepted
 * @param sumDebit the sum of the debit orders
 * @param sumCredit the sum of the credit orders
 * @param messages what the bank said about the orders, in the language chosen for the client
 */
public record ImportResult(
        int errorCode,
        Status status,
        @Nullable Long instructionId,
        @Nullable BigDecimal sumDebit,
        @Nullable BigDecimal sumCredit,
        List<Message> messages) {

    /**
     * Validates the components and copies the messages.
     *
     * @param errorCode the error code
     * @param status the status
     * @param instructionId the batch id
     * @param sumDebit the debit sum
     * @param sumCredit the credit sum
     * @param messages the messages
     */
    public ImportResult {
        requireNonNull(status, "status");
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
     * What the bank said about one order of the batch.
     *
     * @param orderNumber the position of the order in the batch, from 1, or {@code null} when the message is
     *        about the whole batch
     * @param status the status of that order
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
