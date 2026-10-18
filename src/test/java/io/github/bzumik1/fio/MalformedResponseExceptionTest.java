package io.github.bzumik1.fio;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class MalformedResponseExceptionTest {

    private static final String LONG = "x".repeat(1000);
    private static final String QUOTED = "x".repeat(200);

    private static String importAnswer(String result) {
        return "<responseImport><result>" + result + "</result></responseImport>";
    }

    @Test
    void should_keep_the_value_whole_when_excerpted_given_value_of_200_characters() {
        String value = "v".repeat(200);

        assertThat(MalformedResponseException.excerpt(value)).isEqualTo(value);
    }

    @Test
    void should_keep_the_first_200_characters_and_mark_the_cut_when_excerpted_given_value_of_201_characters() {
        assertThat(MalformedResponseException.excerpt("v".repeat(200) + "w")).isEqualTo("v".repeat(200) + "…");
    }

    static Stream<Arguments> quotingSites() {
        return Stream.of(
                argumentSet("account statement account", (ThrowingCallable) () -> new AccountStatementParser().parse(
                        Fixtures.read(Fixtures.PERIODS_STATEMENT).replace("\"accountId\": \"2400222222\"", "\"accountId\": \"" + LONG + "\"")),
                        "x\u2026'", QUOTED),
                argumentSet("account statement number with a large exponent", (ThrowingCallable) () -> new AccountStatementParser().parse(
                        Fixtures.read(Fixtures.PERIODS_STATEMENT).replace("\"openingBalance\": 195.00",
                                "\"openingBalance\": " + "9".repeat(900) + "e2000")),
                        "9\u2026", "9." + "9".repeat(198)),
                argumentSet("import root element", (ThrowingCallable) () -> new ImportResponseParser().parse("<" + LONG + "/>"),
                        "x\u2026>, not a <responseImport>", QUOTED),
                argumentSet("import status", (ThrowingCallable) () -> new ImportResponseParser().parse(
                        importAnswer("<errorCode>0</errorCode><status>" + LONG + "</status>")), "x\u2026'", QUOTED),
                argumentSet("import number", (ThrowingCallable) () -> new ImportResponseParser().parse(
                        importAnswer("<errorCode>" + LONG + "</errorCode><status>ok</status>")), "x\u2026'", QUOTED),
                argumentSet("merchant root element", (ThrowingCallable) () -> new MerchantStatementParser().parse("<" + LONG + "/>"),
                        "x\u2026, not MerchantStatement", QUOTED),
                argumentSet("merchant account", (ThrowingCallable) () -> new MerchantStatementParser().parse(
                        Fixtures.read(Fixtures.MERCHANT_DOCUMENTATION_EXAMPLE).replace("<accountId>2111111111</accountId>",
                                "<accountId>" + LONG + "</accountId>")), "x\u2026'", QUOTED),
                argumentSet("statement number of another shape", (ThrowingCallable) () -> StatementId.parse(LONG), "x\u2026'", QUOTED),
                argumentSet("statement number out of range", (ThrowingCallable) () -> StatementId.parse(" ".repeat(994) + "2026,0"),
                        " \u2026'", " ".repeat(200)));
    }

    @ParameterizedTest
    @MethodSource("quotingSites")
    void should_quote_at_most_200_characters_when_parsed_given_1000_character_value(
            ThrowingCallable parse, String ending, String quoted) {
        MalformedResponseException failure = catchThrowableOfType(MalformedResponseException.class, parse);

        assertThat(failure.getMessage()).endsWith(ending)
                .contains(quoted + "\u2026")
                .doesNotContain(quoted + quoted.charAt(quoted.length() - 1))
                .hasSizeLessThan(400);
    }
}
