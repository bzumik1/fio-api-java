package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;
import io.github.bzumik1.fio.ImportResult.Status;
import io.github.bzumik1.fio.ImportResult.Sum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportResultTest {

    @ParameterizedTest
    @CsvSource({"OK, true", "WARNING, true", "ERROR, false", "FATAL, false"})
    void should_tell_whether_orders_wait_for_authorisation_when_asked_given_status(Status status, boolean accepted) {
        assertThat(new ImportResult(0, status, null, List.of(), List.of()).isAccepted()).isEqualTo(accepted);
    }

    @Test
    void should_keep_own_copies_when_created_given_mutable_lists() {
        List<Sum> sums = new ArrayList<>(List.of(new Sum("CZK", BigDecimal.TEN, null)));
        List<Message> messages = new ArrayList<>(List.of(new Message(1, Status.OK, "OK")));

        ImportResult result = new ImportResult(0, Status.OK, 42L, sums, messages);
        sums.clear();
        messages.clear();

        assertThat(result.sums()).containsExactly(new Sum("CZK", BigDecimal.TEN, null));
        assertThat(result.messages()).containsExactly(new Message(1, Status.OK, "OK"));
        assertThatThrownBy(() -> result.messages().add(new Message(null, null, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_reject_when_created_given_missing_status_or_message_text() {
        assertThatNullPointerException().isThrownBy(() -> new ImportResult(0, null, null, List.of(), List.of()))
                .withMessage("status");
        assertThatNullPointerException().isThrownBy(() -> new Message(1, Status.OK, null)).withMessage("text");
    }
}
