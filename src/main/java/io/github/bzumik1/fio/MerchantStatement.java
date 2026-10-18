package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * One download of a merchant's card transactions: the account, the period and the transactions in it.
 *
 * @param account the account number and bank code
 * @param iban the IBAN of the account
 * @param bic the BIC of the bank
 * @param currency the currency of the account
 * @param dateStart the first day of the period
 * @param dateEnd the last day of the period
 * @param transactions the card transactions, as the bank sends them
 */
public record MerchantStatement(
        DomesticAccount account,
        String iban,
        String bic,
        Currency currency,
        @Nullable LocalDate dateStart,
        @Nullable LocalDate dateEnd,
        List<CardTransaction> transactions) {

    /**
     * Validates the components and copies the transactions.
     *
     * @param account the account
     * @param iban the IBAN
     * @param bic the BIC
     * @param currency the currency
     * @param dateStart the first day
     * @param dateEnd the last day
     * @param transactions the card transactions
     * @throws IllegalArgumentException when a transaction's amount is in another currency than the account's
     */
    public MerchantStatement {
        requireNonNull(account, "account");
        requireNonNull(iban, "iban");
        requireNonNull(bic, "bic");
        requireNonNull(currency, "currency");
        transactions = List.copyOf(transactions);
        for (CardTransaction transaction : transactions) {
            if (!transaction.amount().currency().equals(currency)) {
                throw new IllegalArgumentException("The card transaction " + transaction.operationId() + " is in "
                        + transaction.amount().currency() + ", not in the account's " + currency);
            }
        }
    }
}
