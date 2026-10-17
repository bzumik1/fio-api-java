package io.github.bzumik1.fio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static io.github.bzumik1.fio.FioClient.MINIMUM_REQUEST_INTERVAL;

final class RequestPacer {

    private final RateLimit rateLimit;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Object lock = new Object();
    private Instant nextSlot = Instant.MIN;

    RequestPacer(RateLimit rateLimit, Clock clock, Sleeper sleeper) {
        this.rateLimit = rateLimit;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    <T> T paced(Supplier<T> request, Supplier<String> description) {
        synchronized (lock) {
            Instant deadline = clock.instant().plus(rateLimit.maximumWait());
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
        }
    }

    private void waitForSlot(Instant deadline, Supplier<String> description) {
        Instant now = clock.instant();
        if (!now.isBefore(nextSlot)) {
            return;
        }
        if (nextSlot.isAfter(deadline)) {
            throw new FioRateLimitException(
                    description.get() + " was not sent: the token was used less than "
                            + MINIMUM_REQUEST_INTERVAL.toSeconds() + " seconds ago",
                    null,
                    nextSlot);
        }
        try {
            sleeper.sleep(Duration.between(now, nextSlot));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FioTransportException(description.get() + " was interrupted while waiting for the rate limit", e);
        }
    }
}
