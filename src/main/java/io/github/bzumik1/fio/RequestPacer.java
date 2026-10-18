package io.github.bzumik1.fio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static io.github.bzumik1.fio.FioClient.MINIMUM_REQUEST_INTERVAL;

final class RequestPacer {

    private final RateLimit rateLimit;
    private final Clock clock;
    private final Sleeper sleeper;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Instant nextSlot = Instant.MIN;

    RequestPacer(RateLimit rateLimit, Clock clock, Sleeper sleeper) {
        this.rateLimit = rateLimit;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    <T> T paced(Supplier<T> request, Supplier<String> description) {
        Instant deadline = clock.instant().plus(rateLimit.maximumWait());
        acquire(deadline, description);
        try {
            while (true) {
                waitForSlot(deadline, description);
                nextSlot = clock.instant().plus(MINIMUM_REQUEST_INTERVAL);
                try {
                    return request.get();
                } catch (FioRateLimitException refused) {
                    nextSlot = refused.retryAfter();
                    if (nextSlot.isAfter(deadline)) {
                        throw refused;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void acquire(Instant deadline, Supplier<String> description) {
        boolean acquired;
        try {
            acquired = lock.tryLock(Math.max(0, Duration.between(clock.instant(), deadline).toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FioTransportException(description.get() + " was interrupted while waiting for the rate limit", e, false);
        }
        if (!acquired) {
            Instant now = clock.instant();
            Instant slot = nextSlot;
            throw FioRateLimitException.notSent(
                    description.get() + " was not sent: another request with the token was still in progress when the"
                            + " wait of " + rateLimit.maximumWait() + " ran out",
                    slot.isAfter(now) ? slot : now);
        }
    }

    private void waitForSlot(Instant deadline, Supplier<String> description) {
        Instant now = clock.instant();
        Instant slot = nextSlot;
        if (!now.isBefore(slot)) {
            return;
        }
        if (slot.isAfter(now.plus(MINIMUM_REQUEST_INTERVAL))) {
            nextSlot = now.plus(MINIMUM_REQUEST_INTERVAL);
            slot = nextSlot;
        }
        if (slot.isAfter(deadline)) {
            throw FioRateLimitException.notSent(
                    description.get() + " was not sent: the token was used less than "
                            + MINIMUM_REQUEST_INTERVAL.toSeconds() + " seconds ago",
                    slot);
        }
        try {
            sleeper.sleep(Duration.between(now, slot));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FioTransportException(description.get() + " was interrupted while waiting for the rate limit", e, false);
        }
    }
}
