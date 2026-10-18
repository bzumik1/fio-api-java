package io.github.bzumik1.fio;

import java.math.BigInteger;

final class Iban {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    static boolean hasValidChecksum(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder(rearranged.length() * 2);
        for (char symbol : rearranged.toCharArray()) {
            digits.append(Character.digit(symbol, 36));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
