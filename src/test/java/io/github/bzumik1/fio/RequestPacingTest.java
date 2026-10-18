package io.github.bzumik1.fio;

import io.github.bzumik1.fio.StubFioServer.Hold;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static io.github.bzumik1.fio.Fixtures.PERIODS_STATEMENT;
import static io.github.bzumik1.fio.StubBank.NOW;
import static io.github.bzumik1.fio.StubBank.SECRET;
import static io.github.bzumik1.fio.StubFioServer.TOO_SOON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RequestPacingTest {

    private static final Duration INTERVAL = Duration.ofSeconds(30);

    @RegisterExtension
    final StubBank bank = new StubBank();

    private StubFioServer server() {
        return bank.server();
    }

    private FioClient clientWaitingUpTo(Duration maximumWait) {
        return bank.builder().rateLimit(RateLimit.waitUpTo(maximumWait)).build();
    }

    private FioClient failFast() {
        return bank.builder().rateLimit(RateLimit.failFast()).build();
    }

    private static List<Instant> everyThirtySecondsFromNow(int requests) {
        return IntStream.range(0, requests).mapToObj(index -> NOW.plus(INTERVAL.multipliedBy(index))).toList();
    }

    @Test
    void should_send_second_request_thirty_seconds_after_first_when_called_twice_given_default_rate_limit() {
        server().respondWith(200, "");

        bank.client().bookmark().moveAfterTransaction(1);
        bank.client().bookmark().moveAfterTransaction(2);

        assertThat(server().arrivals()).containsExactly(NOW, NOW.plusSeconds(30));
        assertThat(bank.time().sleeps()).containsExactly(INTERVAL);
    }

    @Test
    void should_send_at_once_when_called_given_thirty_seconds_passed_since_last_request() {
        server().respondWith(200, "");
        bank.client().bookmark().moveAfterTransaction(1);
        bank.time().advance(INTERVAL);

        bank.client().bookmark().moveAfterTransaction(2);

        assertThat(server().arrivals()).containsExactly(NOW, NOW.plusSeconds(30));
        assertThat(bank.time().sleeps()).isEmpty();
    }

    @Test
    void should_retry_thirty_seconds_after_refusal_when_bank_refuses_given_default_rate_limit() {
        server().enqueue(409, TOO_SOON);
        server().respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        AccountStatement statement = bank.client().transactions().sinceBookmark().fetch();

        assertThat(statement.transactions()).hasSize(3);
        assertThat(server().arrivals()).containsExactly(NOW, NOW.plusSeconds(30));
    }

    @Test
    void should_wait_thirty_seconds_from_the_refusal_when_bank_refuses_given_slow_answer() {
        server().enqueueTaking(Duration.ofSeconds(5), 409, TOO_SOON);
        server().respondWith(200, "");

        bank.client().bookmark().moveAfterTransaction(1);

        assertThat(server().arrivals()).containsExactly(NOW, NOW.plusSeconds(35));
        assertThat(bank.time().sleeps()).containsExactly(INTERVAL);
    }

    @ParameterizedTest
    @CsvSource({"59, 2", "60, 3", "89, 3", "90, 4"})
    void should_send_every_request_that_fits_then_raise_last_refusal_when_bank_keeps_refusing_given_maximum_wait(
            long maximumWaitSeconds, int expectedRequests) {
        FioClient patient = clientWaitingUpTo(Duration.ofSeconds(maximumWaitSeconds));
        server().respondWith(409, TOO_SOON);

        FioRateLimitException failure = catchThrowableOfType(FioRateLimitException.class,
                () -> patient.bookmark().moveAfterTransaction(1));

        assertThat(server().arrivals()).containsExactlyElementsOf(everyThirtySecondsFromNow(expectedRequests));
        assertThat(failure.statusCode()).hasValue(409);
        assertThat(failure.responseBody()).hasValue(TOO_SOON);
        assertThat(failure.retryAfter()).isEqualTo(NOW.plus(INTERVAL.multipliedBy(expectedRequests)));
    }

    @Test
    void should_raise_refusal_with_retry_time_after_one_request_when_bank_refuses_given_fail_fast() {
        server().respondWith(409, TOO_SOON);

        FioRateLimitException failure = catchThrowableOfType(FioRateLimitException.class,
                () -> failFast().transactions().sinceBookmark().fetch());

        assertThat(server().requests()).hasSize(1);
        assertThat(failure.statusCode()).hasValue(409);
        assertThat(failure.responseBody()).hasValue(TOO_SOON);
        assertThat(failure.retryAfter()).isEqualTo(NOW.plusSeconds(30));
        assertThat(failure).hasMessageContaining("30 seconds").hasMessageNotContaining(SECRET);
    }

    @Test
    void should_raise_rate_limit_without_status_or_request_when_called_too_soon_given_fail_fast() {
        FioClient failFast = failFast();
        server().respondWith(200, "");
        failFast.bookmark().moveAfterTransaction(1);
        bank.time().advance(Duration.ofSeconds(10));

        FioRateLimitException failure = catchThrowableOfType(FioRateLimitException.class,
                () -> failFast.bookmark().moveAfterTransaction(2));

        assertThat(server().requests()).hasSize(1);
        assertThat(failure.statusCode()).isEmpty();
        assertThat(failure.responseBody()).isEmpty();
        assertThat(failure.retryAfter()).isEqualTo(NOW.plusSeconds(30));
        assertThat(failure).hasMessageContaining("was not sent").hasMessageNotContaining(SECRET);
    }

    @Test
    void should_raise_rate_limit_without_request_when_called_given_next_slot_beyond_maximum_wait() {
        FioClient patient = clientWaitingUpTo(Duration.ofSeconds(10));
        server().respondWith(200, "");
        patient.bookmark().moveAfterTransaction(1);
        bank.time().advance(Duration.ofSeconds(5));

        FioRateLimitException failure = catchThrowableOfType(FioRateLimitException.class,
                () -> patient.bookmark().moveAfterTransaction(2));

        assertThat(server().requests()).hasSize(1);
        assertThat(failure.statusCode()).isEmpty();
        assertThat(failure.retryAfter()).isEqualTo(NOW.plusSeconds(30));
        assertThat(bank.time().sleeps()).isEmpty();
    }

    @Test
    void should_wait_one_interval_from_now_when_called_given_clock_stepped_back() {
        server().respondWith(200, "");
        bank.client().bookmark().moveAfterTransaction(1);
        bank.time().advance(Duration.ofHours(-1));

        bank.client().bookmark().moveAfterTransaction(2);

        assertThat(server().arrivals()).containsExactly(NOW, NOW.minus(Duration.ofHours(1)).plus(INTERVAL));
        assertThat(bank.time().sleeps()).containsExactly(INTERVAL);
    }

    @Test
    void should_raise_transport_failure_and_keep_interrupt_flag_when_waiting_for_slot_given_interrupted_thread() {
        FioClient interruptible = bank.builder().sleeper(duration -> {
            throw new InterruptedException();
        }).build();
        server().respondWith(200, "");
        interruptible.bookmark().moveAfterTransaction(1);

        try {
            FioTransportException failure = catchThrowableOfType(FioTransportException.class,
                    () -> interruptible.bookmark().moveAfterTransaction(2));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure).hasMessageContaining("interrupted while waiting").hasMessageNotContaining(SECRET);
            assertThat(failure.mayHaveReachedTheBank()).isFalse();
            assertThat(server().requests()).hasSize(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void should_raise_transport_failure_without_request_when_waiting_for_lock_given_interrupted_thread() {
        server().respondWith(200, "");
        Thread.currentThread().interrupt();

        try {
            FioTransportException failure = catchThrowableOfType(FioTransportException.class,
                    () -> bank.client().bookmark().moveAfterTransaction(1));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure).hasMessageContaining("interrupted while waiting for the rate limit").hasMessageNotContaining(SECRET);
            assertThat(failure.mayHaveReachedTheBank()).isFalse();
            assertThat(server().requests()).isEmpty();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void should_send_requests_thirty_seconds_apart_when_called_concurrently_given_shared_client() throws Exception {
        int callers = 8;
        FioClient shared = clientWaitingUpTo(Duration.ofMinutes(5));
        server().respondWithPath("application/pdf");
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        List<String> answers = new ArrayList<>();
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int number = 1; number <= callers; number++) {
                int statement = number;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return shared.statements().number(2026, statement).download(StatementFormat.PDF).asText();
                }));
            }
            ready.await();
            go.countDown();
            for (Future<String> result : results) {
                answers.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(server().arrivals()).containsExactlyElementsOf(everyThirtySecondsFromNow(callers));
        assertThat(server().mostRequestsInFlight()).isEqualTo(1);
        assertThat(answers).containsExactlyElementsOf(IntStream.rangeClosed(1, callers)
                .mapToObj(number -> "/v1/rest/by-id/" + SECRET + "/2026/" + number + "/transactions.pdf")
                .toList());
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void should_refuse_at_once_without_status_when_called_during_another_request_given_fail_fast() throws Exception {
        FioClient failFast = failFast();
        server().respondWith(200, "");
        Hold hold = server().holdNextRequest();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> failFast.bookmark().moveAfterTransaction(1));
            hold.awaitArrival();

            FioRateLimitException refused = catchThrowableOfType(FioRateLimitException.class,
                    () -> failFast.bookmark().moveAfterTransaction(2));
            hold.release();
            first.get();

            assertThat(refused.statusCode()).isEmpty();
            assertThat(refused.retryAfter()).isEqualTo(NOW.plusSeconds(30));
            assertThat(refused).hasMessageContaining("still in progress").hasMessageNotContaining(SECRET);
            assertThat(server().requests()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void should_suggest_retrying_now_when_called_during_another_request_given_its_interval_already_passed() throws Exception {
        FioClient failFast = failFast();
        server().respondWith(200, "");
        Hold hold = server().holdNextRequest();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> failFast.bookmark().moveAfterTransaction(1));
            hold.awaitArrival();
            bank.time().advance(Duration.ofSeconds(40));

            FioRateLimitException refused = catchThrowableOfType(FioRateLimitException.class,
                    () -> failFast.bookmark().moveAfterTransaction(2));
            hold.release();
            first.get();

            assertThat(refused.statusCode()).isEmpty();
            assertThat(refused.retryAfter()).isEqualTo(NOW.plusSeconds(40));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void should_fail_without_sending_when_queued_behind_two_callers_given_time_waiting_for_the_lock_counts() throws Exception {
        FioClient patient = clientWaitingUpTo(Duration.ofSeconds(45));
        server().respondWith(200, "");
        Hold hold = server().holdNextRequest();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Thread> queued = new CopyOnWriteArrayList<>();
        Callable<Optional<FioRateLimitException>> queuedCall = () -> {
            queued.add(Thread.currentThread());
            try {
                patient.bookmark().moveAfterTransaction(2);
                return Optional.empty();
            } catch (FioRateLimitException refused) {
                return Optional.of(refused);
            }
        };
        try {
            Future<?> first = pool.submit(() -> patient.bookmark().moveAfterTransaction(1));
            hold.awaitArrival();
            Future<Optional<FioRateLimitException>> second = pool.submit(queuedCall);
            Future<Optional<FioRateLimitException>> third = pool.submit(queuedCall);
            awaitWaitingForLock(queued, List.of(second, third));

            hold.release();
            first.get();
            List<Optional<FioRateLimitException>> outcomes = List.of(second.get(), third.get());

            assertThat(outcomes).filteredOn(Optional::isEmpty).hasSize(1);
            FioRateLimitException refused = outcomes.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            assertThat(refused.statusCode()).isEmpty();
            assertThat(refused.retryAfter()).isEqualTo(NOW.plusSeconds(60));
            assertThat(server().arrivals()).containsExactly(NOW, NOW.plusSeconds(30));
        } finally {
            pool.shutdownNow();
        }
    }

    private static void awaitWaitingForLock(List<Thread> threads, List<? extends Future<?>> calls) {
        while (calls.stream().noneMatch(Future::isDone)
                && (threads.size() < calls.size() || threads.stream().anyMatch(thread -> thread.getState() != Thread.State.TIMED_WAITING))) {
            Thread.onSpinWait();
        }
    }
}
