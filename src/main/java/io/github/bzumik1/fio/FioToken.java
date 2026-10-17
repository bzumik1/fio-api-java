package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.regex.Pattern;

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
    private static final String MASK = "***";

    private final String value;

    private FioToken(String value) {
        this.value = value;
    }

    /**
     * Wraps a token. Surrounding whitespace is dropped, so a token read from a file with a trailing newline works.
     *
     * @param value the token
     * @return the token
     * @throws IllegalArgumentException when the value is blank or holds characters a URL path cannot carry; the
     *         message never repeats the value
     */
    public static FioToken of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A Fio API token is required");
        }
        String stripped = value.strip();
        if (!URL_PATH_SAFE.matcher(stripped).matches()) {
            throw new IllegalArgumentException("A Fio API token may contain only letters, digits, '-' and '_'");
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

    boolean occursIn(@Nullable String text) {
        return text != null && text.contains(value);
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
