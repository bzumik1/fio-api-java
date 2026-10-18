package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class SleeperTest {

    @Test
    void should_end_at_once_with_interruption_when_sleeping_given_interrupted_thread() {
        Thread.currentThread().interrupt();

        Throwable failure = catchThrowable(() -> Sleeper.THREAD.sleep(Duration.ofDays(1)));

        assertThat(failure).isInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isFalse();
    }

    @Test
    void should_return_without_interruption_when_sleeping_given_zero_duration() throws InterruptedException {
        Sleeper.THREAD.sleep(Duration.ZERO);

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }
}
