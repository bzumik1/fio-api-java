package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.argumentSet;

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
    void should_keep_16_kib_of_body_when_created_given_public_limit() {
        String body = "x".repeat(16 * 1024 + 1);

        assertThat(FioException.RESPONSE_BODY_LIMIT).isEqualTo(16 * 1024);
        assertThat(new FioException("refused", 400, body).responseBody()).hasValue("x".repeat(16 * 1024) + "…");
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

    @ParameterizedTest
    @MethodSource("everyFailure")
    void should_tell_whether_the_request_may_have_taken_effect_when_asked_given_each_failure(
            FioException failure, boolean mayHaveTakenEffect) {
        assertThat(failure.mayHaveTakenEffect()).isEqualTo(mayHaveTakenEffect);
    }

    private static Stream<Arguments> everyFailure() {
        Instant slot = Instant.parse("2026-10-17T08:00:30Z");
        return Stream.of(
                argumentSet("other status", new FioException("refused", 404, "Chyba"), false),
                argumentSet("server error", new FioException("refused", 500, "Chyba"), false),
                argumentSet("bad gateway", new FioException("refused", 502, "Bad Gateway"), true),
                argumentSet("gateway timeout", new FioException("refused", 504, "Gateway Timeout"), true),
                argumentSet("no answer", new FioException("no answer", new RuntimeException("boom")), false),
                argumentSet("rate limit refused", new FioRateLimitException("refused", "wait", slot), false),
                argumentSet("rate limit not sent", FioRateLimitException.notSent("not sent", slot), false),
                argumentSet("authorization", new FioAuthorizationException("refused", 401, "bad token"), false),
                argumentSet("authorization with a gateway status", new FioAuthorizationException("refused", 502, ""), false),
                argumentSet("history locked", new FioHistoryLockedException("refused",
                        StubFioServer.historyLockedBody(LocalDate.of(2026, 7, 19))), false),
                argumentSet("too many transactions", new FioTooManyTransactionsException("refused", "too many"), false),
                argumentSet("malformed answer", new FioMalformedResponseException("not understood", 200, "<html>", null), true),
                argumentSet("transport before sending", new FioTransportException("refused", null, false), false),
                argumentSet("transport after sending", new FioTransportException("timed out", null, true), true),
                argumentSet("answer too large", new FioResponseTooLargeException("too long", 1024, null), true));
    }

    @Test
    void should_have_no_status_or_body_but_the_limit_when_created_given_answer_too_large() {
        FioResponseTooLargeException failure = new FioResponseTooLargeException("too long", 1024, new RuntimeException("cut"));

        assertThat(failure.statusCode()).isEmpty();
        assertThat(failure.responseBody()).isEmpty();
        assertThat(failure.limit()).isEqualTo(1024);
        assertThat(failure).hasMessage("too long").cause().hasMessage("cut");
    }
}
