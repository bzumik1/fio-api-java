package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

/**
 * One column of a movement as the bank sent it, before any typing.
 *
 * @param id the id of the column, as in Fio's documentation
 * @param name the name the bank gave the column, or {@code null} when it sent none
 * @param value the raw value, as text; {@code null} when the bank sent {@code null}
 */
public record TransactionColumn(int id, @Nullable String name, @Nullable String value) {
}
