package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Objects.requireNonNull;

/**
 * One account movement.
 *
 * <p>The bank describes a movement by numbered columns. The typed components cover the documented ones; the
 * raw {@link #columns()} keep every column the bank sent, those included, so nothing is lost when Fio adds a
 * column this library does not know yet.
 *
 * @param id the id of the movement, unique and never reused (column 22)
 * @param date the day the movement was booked (column 0)
 * @param amount the amount, negative for money leaving the account (columns 1 and 14)
 * @param type the type of the movement (column 8)
 * @param typeLabel the type as the bank wrote it (column 8)
 * @param counterparty the other side of the movement (columns 2, 3, 10, 12 and 26)
 * @param symbols the variable, constant and specific symbols (columns 4, 5 and 6)
 * @param userIdentification the user identification (column 7)
 * @param executedBy who entered the order (column 9)
 * @param messageForRecipient the message for the recipient (column 16)
 * @param instructionId the id of the order the movement came from (column 17)
 * @param detail the detail (column 18)
 * @param comment the comment (column 25)
 * @param payerReference the payer's reference (column 27)
 * @param columns every column the bank sent, keyed by its id and in ascending order of the keys; a parsed
 *        movement has no {@code null} value and every key equals its column's {@link TransactionColumn#id()}, while
 *        the constructor takes the map as given apart from refusing a {@code null} key
 */
public record Transaction(
        long id,
        LocalDate date,
        Money amount,
        TransactionType type,
        String typeLabel,
        Counterparty counterparty,
        PaymentSymbols symbols,
        @Nullable String userIdentification,
        @Nullable String executedBy,
        @Nullable String messageForRecipient,
        @Nullable Long instructionId,
        @Nullable String detail,
        @Nullable String comment,
        @Nullable String payerReference,
        Map<Integer, TransactionColumn> columns) {

    /**
     * Validates the mandatory components and copies the columns.
     *
     * @param id the id
     * @param date the date
     * @param amount the amount
     * @param type the type
     * @param typeLabel the type label
     * @param counterparty the counterparty
     * @param symbols the symbols
     * @param userIdentification the user identification
     * @param executedBy who entered the order
     * @param messageForRecipient the message
     * @param instructionId the order id
     * @param detail the detail
     * @param comment the comment
     * @param payerReference the payer's reference
     * @param columns the raw columns, copied in ascending order of the keys
     * @throws NullPointerException when a mandatory component or a key of the columns is {@code null}
     */
    public Transaction {
        requireNonNull(date, "date");
        requireNonNull(amount, "amount");
        requireNonNull(type, "type");
        requireNonNull(typeLabel, "typeLabel");
        requireNonNull(counterparty, "counterparty");
        requireNonNull(symbols, "symbols");
        columns = Collections.unmodifiableMap(new TreeMap<>(requireNonNull(columns, "columns")));
    }

    /**
     * Tells whether money arrived on the account.
     *
     * @return whether the amount is positive
     */
    public boolean isIncoming() {
        return amount.isPositive();
    }

    /**
     * Tells whether money left the account.
     *
     * @return whether the amount is negative
     */
    public boolean isOutgoing() {
        return amount.isNegative();
    }
}
