package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

final class MalformedResponseException extends RuntimeException {

    static final String CUT_MARK = "\u2026";

    private static final int EXCERPT_LENGTH = 200;

    @Serial
    private static final long serialVersionUID = 1L;

    MalformedResponseException(String message) {
        super(message);
    }

    MalformedResponseException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    static String excerpt(String value) {
        return excerpt(value, EXCERPT_LENGTH);
    }

    static String excerpt(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length) + CUT_MARK;
    }
}
