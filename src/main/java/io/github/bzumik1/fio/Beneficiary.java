package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import static io.github.bzumik1.fio.PaymentText.countryCode;
import static io.github.bzumik1.fio.PaymentText.optional;
import static io.github.bzumik1.fio.PaymentText.required;

/**
 * The holder of the account a payment abroad goes to.
 *
 * @param name the name, up to 35 characters
 * @param street the street of the address, up to 35 characters
 * @param city the city of the address, up to 35 characters
 * @param country the two-letter country code, such as {@code AT}, from the list of Fio's import schema
 */
public record Beneficiary(String name, @Nullable String street, @Nullable String city, @Nullable String country) {

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
        name = required(name, 35, "beneficiary's name");
        street = optional(street, 35, "beneficiary's street");
        city = optional(city, 35, "beneficiary's city");
        country = country == null ? null : countryCode(country);
    }

    /**
     * Creates a beneficiary known only by name, which is enough for a SEPA payment.
     *
     * @param name the name
     * @return the beneficiary
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
