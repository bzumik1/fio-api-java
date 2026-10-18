package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * A Czech bank account number: an optional prefix of up to 6 digits, a number of up to 10 digits and the 4-digit
 * code of the bank, written {@code 19-2000145399/0800}.
 *
 * <p>The bank code is absent only in a movement the bank sent without one; a payment order needs it.
 *
 * @param prefix the prefix without leading zeros, or {@code null} when there is none
 * @param number the number without leading zeros
 * @param bankCode the 4-digit bank code, or {@code null} when the bank did not send one
 */
public record DomesticAccount(@Nullable String prefix, String number, @Nullable String bankCode) implements BankAccount {

    private static final Pattern FORMAT = Pattern.compile("(?:(\\d{1,6})-)?(\\d{1,10})(?:/(\\d{4}))?");
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    /**
     * Validates and normalises the components: leading zeros are dropped from the prefix and the number, and an
     * all-zero prefix becomes {@code null}.
     *
     * @param prefix the prefix
     * @param number the number
     * @param bankCode the bank code
     * @throws IllegalArgumentException when a component is not digits of the allowed length
     */
    public DomesticAccount {
        prefix = normalisedPrefix(prefix);
        number = digits(requireNonNull(number, "number"), 10, "account number");
        if (bankCode != null && !bankCode.matches("\\d{4}")) {
            throw new IllegalArgumentException("A bank code is 4 digits: '" + bankCode + "'");
        }
    }

    /**
     * Reads an account number written as {@code [prefix-]number[/bankCode]}.
     *
     * @param text the account number
     * @return the account
     * @throws IllegalArgumentException when the text is not an account number
     */
    public static DomesticAccount parse(String text) {
        Matcher matcher = FORMAT.matcher(requireNonNull(text, "text").strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a Czech account number: '" + text + "'");
        }
        return new DomesticAccount(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    /**
     * Creates an account from its number and bank code, with no prefix.
     *
     * @param number the number
     * @param bankCode the bank code
     * @return the account
     * @throws IllegalArgumentException when the number is not up to 10 digits or the bank code not 4 digits
     */
    public static DomesticAccount of(String number, String bankCode) {
        return new DomesticAccount(null, number, requireNonNull(bankCode, "bankCode"));
    }

    /**
     * Returns the account number without the bank code, as {@code prefix-number} or {@code number}.
     *
     * @return the number with its prefix
     */
    public String numberWithPrefix() {
        return prefix == null ? number : prefix + "-" + number;
    }

    /**
     * Returns the Czech IBAN of the account.
     *
     * @return the IBAN
     * @throws IllegalStateException when the bank code is unknown
     */
    public String toIban() {
        if (bankCode == null) {
            throw new IllegalStateException("The IBAN of " + this + " needs the bank code");
        }
        String bban = bankCode + padded(prefix == null ? "0" : prefix, 6) + padded(number, 10);
        BigInteger numeric = new BigInteger(bban + "123500");
        int checksum = 98 - numeric.mod(NINETY_SEVEN).intValue();
        return "CZ" + padded(Integer.toString(checksum), 2) + bban;
    }

    /**
     * Tells whether the prefix and the number pass the Czech National Bank's modulo 11 check, which every account
     * number at a Czech bank passes. A number that fails it is a typing error.
     *
     * @return whether both parts pass the check
     */
    public boolean hasValidChecksum() {
        return weightedModulo11(padded(prefix == null ? "0" : prefix, 6)) && weightedModulo11(padded(number, 10));
    }

    @Override
    public String toString() {
        return bankCode == null ? numberWithPrefix() : numberWithPrefix() + "/" + bankCode;
    }

    private static boolean weightedModulo11(String digits) {
        int[] weights = {6, 3, 7, 9, 10, 5, 8, 4, 2, 1};
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += Character.digit(digits.charAt(i), 10) * weights[weights.length - digits.length() + i];
        }
        return sum % 11 == 0;
    }

    private static @Nullable String normalisedPrefix(@Nullable String prefix) {
        if (prefix == null) {
            return null;
        }
        String stripped = digits(prefix, 6, "account prefix");
        return stripped.equals("0") ? null : stripped;
    }

    private static String digits(String value, int maximumLength, String what) {
        if (!value.matches("\\d{1," + maximumLength + "}")) {
            throw new IllegalArgumentException("An " + what + " is up to " + maximumLength + " digits: '" + value + "'");
        }
        String stripped = value.replaceFirst("^0+(?=\\d)", "");
        return stripped;
    }

    private static String padded(String digits, int length) {
        return "0".repeat(length - digits.length()) + digits;
    }
}
