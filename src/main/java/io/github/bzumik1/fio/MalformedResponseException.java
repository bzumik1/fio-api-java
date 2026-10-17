package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

final class MalformedResponseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    MalformedResponseException(String message) {
        super(message);
    }

    MalformedResponseException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
