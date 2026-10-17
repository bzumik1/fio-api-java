package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static java.util.Objects.requireNonNull;

/**
 * One card transaction of a merchant, from a payment terminal or the payment gateway, as Fio's documentation
 * describes it.
 *
 * @param operationId the id of the card operation
 * @param orderId the id of the card order
 * @param date the day the batch was booked
 * @param amount the amount of the whole batch
 * @param note a note on the transaction
 * @param branchName the branch
 * @param transactionId the id of the transaction
 * @param deviceId the terminal or gateway
 * @param transactionDateTime when the card was used
 * @param authorizationNumber the authorisation number
 * @param cardNumber the masked card number
 * @param transactionAmount the amount of the transaction
 * @param type the type: {@code ON_US} for a Fio card, {@code DOMESTIC} for a Czech card, {@code FOREIGN} otherwise
 * @param cardIssuer the card scheme
 * @param totalFees all fees of the transaction
 * @param fioFee Fio's fee
 * @param interchangeFee the interchange fee
 * @param cardAssociationFee the card scheme's fee
 * @param flexibleCommission the card issuer's fee
 * @param settled whether the transaction was booked
 * @param settlementDate the day the transaction was booked
 * @param variableSymbol the variable symbol entered during the transaction
 */
public record CardTransaction(
        long operationId,
        long orderId,
        LocalDate date,
        BigDecimal amount,
        @Nullable String note,
        @Nullable String branchName,
        @Nullable Long transactionId,
        @Nullable String deviceId,
        @Nullable OffsetDateTime transactionDateTime,
        @Nullable String authorizationNumber,
        @Nullable String cardNumber,
        @Nullable Money transactionAmount,
        @Nullable String type,
        @Nullable String cardIssuer,
        @Nullable BigDecimal totalFees,
        @Nullable BigDecimal fioFee,
        @Nullable BigDecimal interchangeFee,
        @Nullable BigDecimal cardAssociationFee,
        @Nullable BigDecimal flexibleCommission,
        @Nullable Boolean settled,
        @Nullable LocalDate settlementDate,
        @Nullable String variableSymbol) {

    /**
     * Validates the mandatory components.
     *
     * @param operationId the operation id
     * @param orderId the order id
     * @param date the date
     * @param amount the amount
     * @param note the note
     * @param branchName the branch
     * @param transactionId the transaction id
     * @param deviceId the device
     * @param transactionDateTime the time of the transaction
     * @param authorizationNumber the authorisation number
     * @param cardNumber the card number
     * @param transactionAmount the transaction amount
     * @param type the type
     * @param cardIssuer the card scheme
     * @param totalFees all fees
     * @param fioFee Fio's fee
     * @param interchangeFee the interchange fee
     * @param cardAssociationFee the card scheme's fee
     * @param flexibleCommission the issuer's fee
     * @param settled whether booked
     * @param settlementDate the booking day
     * @param variableSymbol the variable symbol
     */
    public CardTransaction {
        requireNonNull(date, "date");
        requireNonNull(amount, "amount");
    }
}
