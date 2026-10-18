package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FioHistoryLockedExceptionTest {

    private static final String BANK_SENTENCE = "Data není možné poskytnout bez silné autorizace. Pokyn k zobrazení dat si"
            + " autorizujte ve Vašem Internetovém bankovnictví a data si vyžádejte znovu. Platnost ověření je 10 minut od"
            + " autorizace. Nebo požádejte o data, která nejsou starší jak 90 dnů (od 19.7.2026).";

    @Test
    void should_read_the_earliest_day_when_asked_given_the_sentence_the_bank_sends() {
        FioHistoryLockedException locked = new FioHistoryLockedException("refused", BANK_SENTENCE);

        assertThat(locked.earliestAvailableDay()).hasValue(LocalDate.of(2026, 7, 19));
        assertThat(locked.statusCode()).hasValue(422);
    }

    @Test
    void should_read_two_digit_day_and_month_when_asked_given_padded_date() {
        FioHistoryLockedException locked = new FioHistoryLockedException("refused", "... (od 01.12.2025).");

        assertThat(locked.earliestAvailableDay()).hasValue(LocalDate.of(2025, 12, 1));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Unprocessable", "(od 2026-07-19)", "od 19.7.2026"})
    void should_have_no_earliest_day_when_asked_given_body_without_the_date(String body) {
        assertThat(new FioHistoryLockedException("refused", body).earliestAvailableDay()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"(od 32.1.2026)", "(od 1.13.2026)", "(od 30.2.2026)"})
    void should_have_no_earliest_day_when_asked_given_date_that_does_not_exist(String body) {
        assertThat(new FioHistoryLockedException("refused", body).earliestAvailableDay()).isEmpty();
    }
}
