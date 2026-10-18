package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RateLimitTest {

    @Test
    void should_wait_two_minutes_at_most_when_created_given_defaults() {
        assertThat(RateLimit.defaults().maximumWait()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void should_equal_explicit_two_minute_wait_when_compared_given_defaults() {
        assertThat(RateLimit.defaults())
                .isEqualTo(RateLimit.waitUpTo(Duration.ofMinutes(2)))
                .hasSameHashCodeAs(RateLimit.waitUpTo(Duration.ofMinutes(2)))
                .isNotEqualTo(RateLimit.waitUpTo(Duration.ofMinutes(3)));
    }

    @Test
    void should_name_the_wait_when_printed_given_waiting_policy() {
        assertThat(RateLimit.defaults()).hasToString("RateLimit[wait up to PT2M]");
    }

    @Test
    void should_never_wait_when_created_given_fail_fast() {
        assertThat(RateLimit.failFast().maximumWait()).isZero();
    }

    @Test
    void should_say_it_fails_fast_when_printed_given_fail_fast() {
        assertThat(RateLimit.failFast()).hasToString("RateLimit[fail fast]");
    }

    @Test
    void should_differ_from_waiting_policy_and_other_types_when_compared_given_fail_fast() {
        assertThat(RateLimit.failFast()).isNotEqualTo(RateLimit.defaults()).isNotEqualTo("RateLimit[fail fast]");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void should_reject_when_created_given_wait_that_is_not_positive(long seconds) {
        assertThatIllegalArgumentException().isThrownBy(() -> RateLimit.waitUpTo(Duration.ofSeconds(seconds)))
                .withMessageContaining("positive");
    }

    @Test
    void should_accept_when_created_given_wait_of_exactly_one_day() {
        assertThat(RateLimit.waitUpTo(Duration.ofDays(1)).maximumWait()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void should_reject_when_created_given_wait_longer_than_one_day() {
        assertThatIllegalArgumentException().isThrownBy(() -> RateLimit.waitUpTo(Duration.ofDays(1).plusNanos(1)))
                .withMessageContaining("PT24H");
    }
}
