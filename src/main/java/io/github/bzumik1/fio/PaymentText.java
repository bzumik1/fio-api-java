package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

final class PaymentText {

    private static final String FIO_BANK_CODE = "2010";
    private static final int CENT_DECIMALS = 2;
    private static final int INTEGER_DIGITS = 16;
    private static final Pattern XML_INCOMPATIBLE = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    private PaymentText() {
    }

    static @Nullable String optional(@Nullable String value, int maximumLength, String name) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        if (stripped.length() > maximumLength) {
            throw new IllegalArgumentException(
                    "The " + name + " is at most " + maximumLength + " characters, not " + stripped.length());
        }
        if (XML_INCOMPATIBLE.matcher(stripped).find()) {
            throw new IllegalArgumentException("The " + name + " holds a control character");
        }
        return stripped;
    }

    static String required(String value, int maximumLength, String name) {
        String stripped = optional(requireNonNull(value, name), maximumLength, name);
        if (stripped == null) {
            throw new IllegalArgumentException("The " + name + " is blank");
        }
        return stripped;
    }

    static <T> T required(@Nullable T value, String name) {
        if (value == null) {
            throw new IllegalStateException("The " + name + " is required");
        }
        return value;
    }

    static Money positive(@Nullable Money amount) {
        Money checked = required(amount, "amount");
        if (!checked.isPositive()) {
            throw new IllegalArgumentException("A payment amount is positive: " + checked);
        }
        if ((long) checked.amount().precision() - checked.amount().scale() > INTEGER_DIGITS) {
            throw new IllegalArgumentException("A payment amount has at most " + INTEGER_DIGITS + " digits before the decimal point: " + checked);
        }
        if (checked.amount().stripTrailingZeros().scale() > CENT_DECIMALS) {
            throw new IllegalArgumentException("A payment amount has at most two decimal places: " + checked);
        }
        return checked;
    }

    static DomesticAccount payerAccount(DomesticAccount account) {
        requireNonNull(account, "from");
        if (account.prefix() != null) {
            throw new IllegalArgumentException("The payer's account is a Fio account, which has no prefix: " + account);
        }
        if (account.bankCode() != null && !account.bankCode().equals(FIO_BANK_CODE)) {
            throw new IllegalArgumentException(
                    "The payer's account is a Fio account with bank code " + FIO_BANK_CODE + ": " + account);
        }
        return account;
    }

    static @Nullable String digits(@Nullable String value, int length, String name) {
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d{" + length + "}")) {
            throw new IllegalArgumentException("The " + name + " is " + length + " digits: '" + value + "'");
        }
        return value;
    }

    static String bic(String value) {
        requireNonNull(value, "bic");
        String stripped = value.strip().toUpperCase(Locale.ROOT);
        if (!stripped.matches("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?")) {
            throw new IllegalArgumentException("Not a BIC: '" + value + "'");
        }
        return stripped;
    }

    static String iban(String value) {
        requireNonNull(value, "iban");
        String stripped = value.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!stripped.matches("[A-Z]{2}\\d{2}[A-Z0-9]{1,30}")) {
            throw new IllegalArgumentException("Not an IBAN: '" + value + "'");
        }
        if (!Iban.hasValidChecksum(stripped)) {
            throw new IllegalArgumentException("Not an IBAN, the check digits do not match: '" + value + "'");
        }
        return stripped;
    }

    static String countryCode(String value) {
        requireNonNull(value, "country");
        String stripped = value.strip().toUpperCase(Locale.ROOT);
        if (!stripped.matches("[A-Z]{2,3}")) {
            throw new IllegalArgumentException("Not a country code: '" + value + "'");
        }
        return stripped;
    }
}
