package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

/**
 * The variable, constant and specific symbols of a Czech payment. They stay strings because leading zeros are
 * part of them ({@code "0558"}); the bank sends {@code "0"} for a symbol a payer left at zero, which this library
 * passes on unchanged.
 *
 * @param variable the variable symbol, up to 10 digits
 * @param constant the constant symbol, up to 4 digits
 * @param specific the specific symbol, up to 10 digits
 */
public record PaymentSymbols(@Nullable String variable, @Nullable String constant, @Nullable String specific) {

    private static final PaymentSymbols NONE = new PaymentSymbols(null, null, null);

    /**
     * Validates the symbols.
     *
     * @param variable the variable symbol
     * @param constant the constant symbol
     * @param specific the specific symbol
     * @throws IllegalArgumentException when a symbol is not digits of the allowed length
     */
    public PaymentSymbols {
        variable = checked(variable, 10, "variable");
        constant = checked(constant, 4, "constant");
        specific = checked(specific, 10, "specific");
    }

    /**
     * Returns no symbols.
     *
     * @return symbols with every part {@code null}
     */
    public static PaymentSymbols none() {
        return NONE;
    }

    /**
     * Returns only a variable symbol.
     *
     * @param variable the variable symbol
     * @return the symbols
     */
    public static PaymentSymbols variable(String variable) {
        return new PaymentSymbols(variable, null, null);
    }

    /**
     * Tells whether no symbol is set.
     *
     * @return whether every part is {@code null}
     */
    public boolean isEmpty() {
        return variable == null && constant == null && specific == null;
    }

    private static @Nullable String checked(@Nullable String symbol, int maximumLength, String name) {
        if (symbol != null && !symbol.matches("\\d{1," + maximumLength + "}")) {
            throw new IllegalArgumentException(
                    "A " + name + " symbol is up to " + maximumLength + " digits: '" + symbol + "'");
        }
        return symbol;
    }
}
