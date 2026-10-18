package io.github.bzumik1.fio;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class Fixtures {

    static final String PERIODS_STATEMENT = "periods-statement.json";
    static final String DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES = "documentation-example-epoch-dates.json";
    static final String STATEMENT_2012_3 = "documentation-statement-2012-3.json";
    static final String MERCHANT_DOCUMENTATION_EXAMPLE = "merchant-documentation-example.xml";
    static final String MERCHANT_SCHEMA_EXAMPLE = "merchant-schema-example.xml";
    static final String IMPORT_ACCEPTED = "import-accepted.xml";
    static final String IMPORT_REFUSED = "import-refused.xml";
    static final String DOMESTIC_PAYMENT_EXAMPLE = "payment-documentation-domestic.xml";
    static final String EURO_PAYMENT_EXAMPLE = "payment-documentation-euro.xml";
    static final String FOREIGN_PAYMENT_EXAMPLE = "payment-documentation-foreign.xml";

    private Fixtures() {
    }

    static String read(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }

    static byte[] bytes(String name) {
        try (InputStream stream = Fixtures.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
