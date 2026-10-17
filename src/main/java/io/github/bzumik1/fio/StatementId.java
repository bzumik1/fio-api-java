package io.github.bzumik1.fio;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * The number of an official account statement: Fio numbers them from 1 within each year.
 *
 * @param year the year of the statement
 * @param number the number of the statement within the year, from 1
 */
public record StatementId(int year, int number) implements Comparable<StatementId> {

    private static final Pattern FIO_FORMAT = Pattern.compile("\\s*(\\d{4}),(\\d{1,3})\\s*");

    /**
     * Validates the components.
     *
     * @param year the year
     * @param number the number
     * @throws IllegalArgumentException when the year is not four digits or the number is below 1
     */
    public StatementId {
        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("A statement year has four digits: " + year);
        }
        if (number < 1) {
            throw new IllegalArgumentException("Statements are numbered from 1: " + number);
        }
    }

    /**
     * Reads the number as the bank writes it, {@code year,number}, for example {@code 2026,10}.
     *
     * @param text the number
     * @return the statement number
     * @throws MalformedResponseException when the text has another shape
     */
    static StatementId parse(String text) {
        Matcher matcher = FIO_FORMAT.matcher(requireNonNull(text, "text"));
        if (!matcher.matches()) {
            throw new MalformedResponseException("Not a statement number: '" + text + "'");
        }
        return new StatementId(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    @Override
    public int compareTo(StatementId other) {
        return year != other.year ? Integer.compare(year, other.year) : Integer.compare(number, other.number);
    }

    @Override
    public String toString() {
        return year + "/" + number;
    }
}
