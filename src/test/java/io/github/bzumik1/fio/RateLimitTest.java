package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RateLimitTest {

    @Test
    void should_wait_two_minutes_at_most_given_defaults() {
        assertThat(RateLimit.defaults().maximumWait()).isEqualTo(Duration.ofMinutes(2));
        assertThat(RateLimit.defaults()).isEqualTo(RateLimit.waitUpTo(Duration.ofMinutes(2)))
                .hasSameHashCodeAs(RateLimit.waitUpTo(Duration.ofMinutes(2)))
                .hasToString("RateLimit[wait up to PT2M]");
    }

    @Test
    void should_never_wait_given_fail_fast() {
        assertThat(RateLimit.failFast().maximumWait()).isZero();
        assertThat(RateLimit.failFast()).hasToString("RateLimit[fail fast]")
                .isNotEqualTo(RateLimit.defaults())
                .isNotEqualTo("RateLimit[fail fast]");
    }

    @Test
    void should_reject_when_created_given_wait_that_is_not_positive() {
        assertThatIllegalArgumentException().isThrownBy(() -> RateLimit.waitUpTo(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> RateLimit.waitUpTo(Duration.ofSeconds(-1)));
    }
}
