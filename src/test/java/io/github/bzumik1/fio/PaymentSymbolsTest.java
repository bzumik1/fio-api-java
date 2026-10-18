package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PaymentSymbolsTest {

    static Stream<Arguments> symbols() {
        return Stream.of(
                Arguments.of(PaymentSymbols.none(), true),
                Arguments.of(PaymentSymbols.ofVariable("0558"), false),
                Arguments.of(new PaymentSymbols(null, "0558", null), false),
                Arguments.of(new PaymentSymbols(null, null, "1"), false));
    }

    @ParameterizedTest
    @MethodSource("symbols")
    void should_tell_whether_any_symbol_is_set_when_asked_given_symbols(PaymentSymbols symbols, boolean empty) {
        assertThat(symbols.isEmpty()).isEqualTo(empty);
    }

    @Test
    void should_set_only_the_variable_symbol_when_created_given_variable_symbol() {
        assertThat(PaymentSymbols.ofVariable("0558")).isEqualTo(new PaymentSymbols("0558", null, null));
    }

    @Test
    void should_keep_leading_zeros_when_created_given_symbols_of_the_longest_lengths() {
        assertThat(new PaymentSymbols("0000000001", "0558", "0000000002"))
                .returns("0000000001", PaymentSymbols::variable)
                .returns("0558", PaymentSymbols::constant)
                .returns("0000000002", PaymentSymbols::specific);
    }

    @ParameterizedTest
    @CsvSource({
            "variable, 12345678901, 10", "variable, abc, 10", "variable, '', 10", "variable, 12 34, 10",
            "constant, 05580, 4", "constant, abc, 4", "constant, '', 4",
            "specific, 12345678901, 10", "specific, 1a, 10", "specific, '', 10"
    })
    void should_reject_naming_the_symbol_when_created_given_symbol_that_is_not_its_digits(String kind, String symbol, int digits) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentSymbols(
                        kind.equals("variable") ? symbol : null,
                        kind.equals("constant") ? symbol : null,
                        kind.equals("specific") ? symbol : null))
                .withMessage("A " + kind + " symbol is up to " + digits + " digits: '" + symbol + "'");
    }
}
