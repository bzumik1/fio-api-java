package io.github.bzumik1.fio;

import java.io.PrintWriter;
import java.io.StringWriter;

final class Throwables {

    private Throwables() {
    }

    static String printed(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
