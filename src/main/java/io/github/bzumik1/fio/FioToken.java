package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * The API token of one account, created in Fio internet banking (Nastavení, API). It becomes usable five minutes
 * after it is authorised and lives at most 180 days unless renewed; a "Sledování účtu" (account watching) token is
 * enough for every download, and submitting payments needs one allowed to do so.
 *
 * <p>The token is the only credential, so it never shows: {@link #toString()} prints {@code FioToken[***]}, and
 * every message, URL and response body a {@link FioException} carries has the token replaced by {@code ***}.
 */
public final class FioToken {

    private static final Pattern URL_PATH_SAFE = Pattern.compile("[A-Za-z0-9_-]+");
    private static final int MINIMUM_LENGTH = 16;
    private static final String MASK = "***";
    private static final int SHORTEST_MASKED_BEGINNING = 12;

    private final String value;

    private FioToken(String value) {
        this.value = value;
    }

    /**
     * Wraps a token. Surrounding whitespace is dropped, so a token read from a file with a trailing newline works.
     *
     * @param value the token
     * @return the token
     * @throws IllegalArgumentException when the value is blank, shorter than 16 characters (Fio issues 64) or
     *         holds characters a URL path cannot carry; the message never repeats the value
     */
    public static FioToken of(String value) {
        String stripped = requireNonNull(value, "value").strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("A Fio API token is required");
        }
        if (!URL_PATH_SAFE.matcher(stripped).matches()) {
            throw new IllegalArgumentException("A Fio API token may contain only letters, digits, '-' and '_'");
        }
        if (stripped.length() < MINIMUM_LENGTH) {
            throw new IllegalArgumentException("A Fio API token has at least " + MINIMUM_LENGTH + " characters");
        }
        return new FioToken(stripped);
    }

    /**
     * Reads the token from an environment variable.
     *
     * @param variable the name of the variable, such as {@code FIO_TOKEN}
     * @return the token
     * @throws IllegalArgumentException when the variable is not set or does not hold a token
     */
    public static FioToken fromEnvironment(String variable) {
        return fromEnvironment(variable, System::getenv);
    }

    static FioToken fromEnvironment(String variable, Function<String, @Nullable String> environment) {
        String value = environment.apply(variable);
        if (value == null) {
            throw new IllegalArgumentException("The environment variable " + variable + " is not set");
        }
        return of(value);
    }

    String value() {
        return value;
    }

    @Nullable String mask(@Nullable String text) {
        return text == null ? null : text.replace(value, MASK);
    }

    String maskExcerpt(String text) {
        String masked = text.replace(value, MASK);
        StringBuilder result = new StringBuilder(masked.length());
        int position = 0;
        while (position < masked.length()) {
            int beginning = beginningOfTokenAt(masked, position);
            if (beginning >= SHORTEST_MASKED_BEGINNING) {
                result.append(MASK);
                position += beginning;
            } else {
                result.append(masked.charAt(position));
                position++;
            }
        }
        return result.toString();
    }

    boolean occursIn(@Nullable String text) {
        return text != null && !maskExcerpt(text).equals(text);
    }

    private int beginningOfTokenAt(String text, int position) {
        int longest = Math.min(value.length(), text.length() - position);
        int length = 0;
        while (length < longest && text.charAt(position + length) == value.charAt(length)) {
            length++;
        }
        return length;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof FioToken token && token.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "FioToken[" + MASK + "]";
    }
}
