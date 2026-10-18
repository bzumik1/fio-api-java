package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FioExceptionTest {

    @Test
    void should_keep_whole_body_when_created_given_body_at_the_limit() {
        String body = "x".repeat(FioException.RESPONSE_BODY_LIMIT);

        assertThat(new FioException("refused", 400, body).responseBody()).hasValue(body);
    }

    @Test
    void should_cut_body_with_ellipsis_when_created_given_body_over_the_limit() {
        String body = "x".repeat(FioException.RESPONSE_BODY_LIMIT) + "tail";

        assertThat(new FioException("refused", 400, body).responseBody())
                .hasValue("x".repeat(FioException.RESPONSE_BODY_LIMIT) + "…");
    }

    @Test
    void should_have_no_status_or_body_when_created_given_no_answer() {
        FioException failure = new FioException("no answer", new RuntimeException("boom"));

        assertThat(failure.statusCode()).isEmpty();
        assertThat(failure.responseBody()).isEmpty();
        assertThat(failure.getCause()).hasMessage("boom");
    }

    @Test
    void should_have_no_status_when_created_given_request_not_sent_for_rate_limit() {
        Instant slot = Instant.parse("2026-10-17T08:00:30Z");

        FioRateLimitException refused = FioRateLimitException.notSent("not sent", slot);

        assertThat(refused.statusCode()).isEmpty();
        assertThat(refused.responseBody()).isEmpty();
        assertThat(refused.retryAfter()).isEqualTo(slot);
        assertThat(refused).hasMessage("not sent");
    }

    @Test
    void should_have_conflict_status_when_created_given_refusal_from_the_bank() {
        Instant slot = Instant.parse("2026-10-17T08:00:30Z");

        FioRateLimitException refused = new FioRateLimitException("refused", "wait", slot);

        assertThat(refused.statusCode()).hasValue(409);
        assertThat(refused.responseBody()).hasValue("wait");
    }
}
