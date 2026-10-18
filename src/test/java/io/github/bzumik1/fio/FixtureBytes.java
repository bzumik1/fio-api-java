package io.github.bzumik1.fio;

final class FixtureBytes {

    static final String STATEMENT_2012_3 = Fixtures.STATEMENT_2012_3;
    static final String DOMESTIC_PAYMENT_EXAMPLE = Fixtures.DOMESTIC_PAYMENT_EXAMPLE;
    static final String EURO_PAYMENT_EXAMPLE = Fixtures.EURO_PAYMENT_EXAMPLE;
    static final String FOREIGN_PAYMENT_EXAMPLE = Fixtures.FOREIGN_PAYMENT_EXAMPLE;

    private FixtureBytes() {
    }

    static byte[] read(String name) {
        return Fixtures.bytes(name);
    }
}
