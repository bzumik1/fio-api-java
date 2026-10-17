package io.github.bzumik1.fio;

import java.math.BigDecimal;
import java.util.Currency;

import static java.util.Objects.requireNonNull;

/**
 * An amount in a currency, exactly as the bank sends it: the scale is kept, so {@code 1.00} stays {@code 1.00}.
 *
 * @param amount the amount, negative for money leaving the account
 * @param currency the ISO 4217 currency
 */
public record Money(BigDecimal amount, Currency currency) {

    private static final Currency CZK = Currency.getInstance("CZK");

    /**
     * Validates the components.
     *
     * @param amount the amount
     * @param currency the currency
     */
    public Money {
        requireNonNull(amount, "amount");
        requireNonNull(currency, "currency");
    }

    /**
     * Creates an amount from its textual forms.
     *
     * @param amount the amount as {@link BigDecimal} reads it, such as {@code "100.00"}
     * @param currencyCode the ISO 4217 code, such as {@code "CZK"}
     * @return the money
     * @throws IllegalArgumentException when the amount is not a number or the code is not a known currency
     */
    public static Money of(String amount, String currencyCode) {
        requireNonNull(amount, "amount");
        requireNonNull(currencyCode, "currencyCode");
        try {
            return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not an amount: '" + amount + "'", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not an ISO 4217 currency code: '" + currencyCode + "'", e);
        }
    }

    /**
     * Creates an amount in Czech koruna.
     *
     * @param amount the amount as {@link BigDecimal} reads it, such as {@code "100.00"}
     * @return the money
     */
    public static Money czk(String amount) {
        return of(amount, CZK.getCurrencyCode());
    }

    /**
     * Creates an amount in Czech koruna.
     *
     * @param amount the amount
     * @return the money
     */
    public static Money czk(BigDecimal amount) {
        return new Money(amount, CZK);
    }

    /**
     * Tells whether the amount is above zero, which for a movement means money arriving on the account.
     *
     * @return whether the amount is positive
     */
    public boolean isPositive() {
        return amount.signum() > 0;
    }

    /**
     * Tells whether the amount is below zero, which for a movement means money leaving the account.
     *
     * @return whether the amount is negative
     */
    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
