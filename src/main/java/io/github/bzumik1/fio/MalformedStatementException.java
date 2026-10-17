package io.github.bzumik1.fio;

final class MalformedStatementException extends RuntimeException {

    MalformedStatementException(String message) {
        super(message);
    }

    MalformedStatementException(String message, Throwable cause) {
        super(message, cause);
    }
}
