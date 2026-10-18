package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Timeout(5)
class SleeperTest {

    @Test
    void should_block_for_the_duration_when_sleeping_given_fifty_milliseconds() throws InterruptedException {
        long before = System.nanoTime();

        Sleeper.THREAD.sleep(Duration.ofMillis(50));

        assertThat(Duration.ofNanos(System.nanoTime() - before)).isGreaterThanOrEqualTo(Duration.ofMillis(50));
    }

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
