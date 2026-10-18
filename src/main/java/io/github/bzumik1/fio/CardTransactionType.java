package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

/**
 * Where a card was issued relative to the merchant's bank, as the bank classifies a card transaction. Constants
 * may be added in a minor release, so a {@code switch} over this type needs a {@code default} branch.
 */
public enum CardTransactionType {

    /** A card issued by Fio banka. */
    ON_US,

    /** A card issued by another Czech bank. */
    DOMESTIC,

    /** A card issued abroad. */
    FOREIGN,

    /** A classification this library does not know; {@link CardTransaction#typeLabel()} holds the bank's text. */
    OTHER;

    /**
     * Maps the bank's text to a type.
     *
     * @param label the text the bank sent, such as {@code ON_US}
     * @return the type, or {@link #OTHER} for an unknown text
     */
    public static CardTransactionType fromLabel(String label) {
        String stripped = label.strip();
        for (CardTransactionType type : values()) {
            if (type != OTHER && type.name().equals(stripped)) {
                return type;
            }
        }
        return OTHER;
    }

    static @Nullable CardTransactionType fromOptionalLabel(@Nullable String label) {
        return label == null ? null : fromLabel(label);
    }
}
