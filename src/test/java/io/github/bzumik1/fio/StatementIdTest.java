package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementIdTest {

    @Test
    void should_order_by_number_when_compared_given_statements_of_one_year() {
        assertThat(new StatementId(2026, 2)).isGreaterThan(new StatementId(2026, 1));
    }

    @Test
    void should_order_by_year_first_when_compared_given_statements_of_different_years() {
        assertThat(new StatementId(2026, 12)).isLessThan(new StatementId(2027, 1));
    }

    @Test
    void should_compare_as_equal_when_compared_given_same_year_and_number() {
        assertThat(new StatementId(2026, 2)).isEqualByComparingTo(new StatementId(2026, 2)).isEqualTo(new StatementId(2026, 2));
    }

    @Test
    void should_print_year_slash_number_when_printed_given_statement_id() {
        assertThat(new StatementId(2026, 2)).hasToString("2026/2");
    }

    @ParameterizedTest
    @CsvSource({"999, 1", "10000, 1", "2026, 0", "2026, -1"})
    void should_reject_when_created_given_year_or_number_out_of_range(int year, int number) {
        assertThatIllegalArgumentException().isThrownBy(() -> new StatementId(year, number));
    }

    @ParameterizedTest
    @CsvSource(value = {"2026,10|2026|10", "' 2026,10\n'|2026|10", "2012,3|2012|3", "2012,999|2012|999"}, delimiter = '|')
    void should_read_bank_format_when_parsed_given_statement_number_text(String text, int year, int number) {
        assertThat(StatementId.parse(text)).isEqualTo(new StatementId(year, number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026/10", "2026,", ",10", "26,10", "2026,1000", "2026,10,1", "", "2026, 10"})
    void should_reject_as_malformed_when_parsed_given_text_of_another_shape(String text) {
        assertThatThrownBy(() -> StatementId.parse(text))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("Not a statement number: '" + text + "'")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026,0", "0999,1", "2026,000"})
    void should_reject_as_malformed_with_the_cause_when_parsed_given_year_or_number_out_of_range(String text) {
        assertThatThrownBy(() -> StatementId.parse(text))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("Not a statement number: '" + text + "'")
                .hasCauseExactlyInstanceOf(IllegalArgumentException.class);
    }
}
