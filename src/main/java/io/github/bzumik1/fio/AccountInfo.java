package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Currency;

import static java.util.Objects.requireNonNull;

/**
 * The {@code info} block of a statement: the account and the period or statement the download covers.
 *
 * <p>The period is the one that was asked for, not the span of the movements found in it; both ends are
 * {@code null} in a download since the bookmark. The movement ids are {@code null} when the download holds no
 * movements, and the bookmark id is {@code null} for a download of a period.
 *
 * @param account the account number and bank code
 * @param iban the IBAN of the account
 * @param bic the BIC of the bank
 * @param currency the currency of the account
 * @param openingBalance the balance at the start of the period
 * @param closingBalance the balance at the end of the period
 * @param dateStart the first day of the period
 * @param dateEnd the last day of the period
 * @param statement the number of the official statement, in a download of one
 * @param firstTransactionId the id of the first movement in the download
 * @param lastTransactionId the id of the last movement in the download
 * @param bookmarkTransactionId the id of the movement the bookmark pointed at before this download
 */
public record AccountInfo(
        DomesticAccount account,
        String iban,
        String bic,
        Currency currency,
        Money openingBalance,
        Money closingBalance,
        @Nullable LocalDate dateStart,
        @Nullable LocalDate dateEnd,
        @Nullable StatementId statement,
        @Nullable Long firstTransactionId,
        @Nullable Long lastTransactionId,
        @Nullable Long bookmarkTransactionId) {

    /**
     * Validates the mandatory components.
     *
     * @param account the account
     * @param iban the IBAN
     * @param bic the BIC
     * @param currency the currency
     * @param openingBalance the opening balance
     * @param closingBalance the closing balance
     * @param dateStart the first day
     * @param dateEnd the last day
     * @param statement the statement number
     * @param firstTransactionId the first movement id
     * @param lastTransactionId the last movement id
     * @param bookmarkTransactionId the bookmark id
     * @throws IllegalArgumentException when a balance is not in the account's currency
     */
    public AccountInfo {
        requireNonNull(account, "account");
        requireNonNull(iban, "iban");
        requireNonNull(bic, "bic");
        requireNonNull(currency, "currency");
        requireNonNull(openingBalance, "openingBalance");
        requireNonNull(closingBalance, "closingBalance");
        if (!openingBalance.currency().equals(currency) || !closingBalance.currency().equals(currency)) {
            throw new IllegalArgumentException("The balances are in " + openingBalance.currency() + " and "
                    + closingBalance.currency() + ", not in the account's " + currency);
        }
    }
}
