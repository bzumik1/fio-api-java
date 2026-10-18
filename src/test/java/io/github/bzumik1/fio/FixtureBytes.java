package io.github.bzumik1.fio;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

final class FixtureBytes {

    static final String STATEMENT_2012_3 = "documentation-statement-2012-3.json";
    static final String DOMESTIC_PAYMENT_EXAMPLE = "payment-documentation-domestic.xml";
    static final String EURO_PAYMENT_EXAMPLE = "payment-documentation-euro.xml";
    static final String FOREIGN_PAYMENT_EXAMPLE = "payment-documentation-foreign.xml";

    private FixtureBytes() {
    }

    static byte[] read(String name) {
        try (InputStream stream = FixtureBytes.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
