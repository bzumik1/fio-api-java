package io.github.bzumik1.fio;

import static java.util.Objects.requireNonNull;

/**
 * An account abroad, identified as the bank sent it, usually by IBAN.
 *
 * @param identifier the account identifier, such as an IBAN
 */
public record ForeignAccount(String identifier) implements BankAccount {

    /**
     * Validates the identifier.
     *
     * @param identifier the account identifier
     * @throws IllegalArgumentException when the identifier is blank
     */
    public ForeignAccount {
        requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("A foreign account needs an identifier");
        }
    }

    @Override
    public String toString() {
        return identifier;
    }
}
