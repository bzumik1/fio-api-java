package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static java.util.Objects.requireNonNull;

/**
 * One card transaction of a merchant, from a payment terminal or the payment gateway, as Fio's documentation
 * describes it. Every amount and fee is in the currency of the account, except {@link #transactionAmount()}, which
 * is in the currency the card was charged in.
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
 * @param transactionAmount the amount of the transaction, in the currency it was charged in
 * @param type where the card was issued, or {@code null} when the bank did not say
 * @param typeLabel the bank's text for the type, or {@code null} when the bank did not say
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
        Money amount,
        @Nullable String note,
        @Nullable String branchName,
        @Nullable Long transactionId,
        @Nullable String deviceId,
        @Nullable OffsetDateTime transactionDateTime,
        @Nullable String authorizationNumber,
        @Nullable String cardNumber,
        @Nullable Money transactionAmount,
        @Nullable CardTransactionType type,
        @Nullable String typeLabel,
        @Nullable String cardIssuer,
        @Nullable Money totalFees,
        @Nullable Money fioFee,
        @Nullable Money interchangeFee,
        @Nullable Money cardAssociationFee,
        @Nullable Money flexibleCommission,
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
     * @param typeLabel the bank's text for the type
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
