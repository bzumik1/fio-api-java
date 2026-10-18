package io.github.bzumik1.fio;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CounterpartyTest {

    static Stream<Arguments> counterparties() {
        return Stream.of(
                Arguments.of(Counterparty.none(), true),
                Arguments.of(new Counterparty(null, "Novák", null, null), false),
                Arguments.of(new Counterparty(DomesticAccount.parse("2400222222/2010"), null, null, null), false),
                Arguments.of(new Counterparty(null, null, "Fio banka", null), false),
                Arguments.of(new Counterparty(null, null, null, "FIOBCZPP"), false));
    }

    @ParameterizedTest
    @MethodSource("counterparties")
    void should_tell_whether_anything_is_known_when_asked_given_counterparty(Counterparty counterparty, boolean empty) {
        assertThat(counterparty.isEmpty()).isEqualTo(empty);
    }
}
