package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import static io.github.bzumik1.fio.PaymentText.countryCode;
import static io.github.bzumik1.fio.PaymentText.optional;
import static io.github.bzumik1.fio.PaymentText.required;

/**
 * The holder of the account a payment abroad goes to.
 *
 * @param name the name, up to 50 characters (the limit of Fio's import schema; the documentation says 35)
 * @param street the street of the address, up to 50 characters
 * @param city the city of the address, up to 50 characters
 * @param country the two-letter country code, such as {@code AT}, from the list of Fio's import schema
 */
public record Beneficiary(String name, @Nullable String street, @Nullable String city, @Nullable String country) {

    private static final int SCHEMA_LIMIT = 50;

    /**
     * Validates the components.
     *
     * @param name the name
     * @param street the street
     * @param city the city
     * @param country the country code
     * @throws IllegalArgumentException when a text is too long or holds a character XML cannot carry, or the
     *         country is not a code of Fio's list
     */
    public Beneficiary {
        name = required(name, SCHEMA_LIMIT, "beneficiary's name");
        street = optional(street, SCHEMA_LIMIT, "beneficiary's street");
        city = optional(city, SCHEMA_LIMIT, "beneficiary's city");
        country = country == null ? null : countryCode(country);
    }

    /**
     * Creates a beneficiary known only by name, which is enough for a SEPA payment.
     *
     * @param name the name
     * @return the beneficiary
     * @throws IllegalArgumentException when the name is blank, longer than 50 characters or holds a character XML
     *         cannot carry
     */
    public static Beneficiary named(String name) {
        return new Beneficiary(name, null, null, null);
    }

    /**
     * Tells whether the address is complete, which a payment outside SEPA needs.
     *
     * @return whether street, city and country are all set
     */
    public boolean hasFullAddress() {
        return street != null && city != null && country != null;
    }
}
