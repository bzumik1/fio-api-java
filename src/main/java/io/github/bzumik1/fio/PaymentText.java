package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

final class PaymentText {

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
        return stripped;
    }

    static String required(@Nullable String value, int maximumLength, String name) {
        String stripped = optional(value, maximumLength, name);
        if (stripped == null) {
            throw new IllegalStateException("The " + name + " is required");
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
        return checked;
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
        String stripped = value.strip().toUpperCase();
        if (!stripped.matches("[A-Z0-9]{8}([A-Z0-9]{3})?")) {
            throw new IllegalArgumentException("Not a BIC: '" + value + "'");
        }
        return stripped;
    }

    static String iban(String value) {
        requireNonNull(value, "iban");
        String stripped = value.replace(" ", "").toUpperCase();
        if (!stripped.matches("[A-Z]{2}\\d{2}[A-Z0-9]{1,30}")) {
            throw new IllegalArgumentException("Not an IBAN: '" + value + "'");
        }
        return stripped;
    }

    static String countryCode(String value) {
        requireNonNull(value, "country");
        String stripped = value.strip().toUpperCase();
        if (!stripped.matches("[A-Z]{2,3}")) {
            throw new IllegalArgumentException("Not a country code: '" + value + "'");
        }
        return stripped;
    }
}
