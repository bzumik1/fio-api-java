package io.github.bzumik1.fio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class FakeTime {

    private final AtomicReference<Instant> now;
    private final List<Duration> sleeps = new ArrayList<>();

    FakeTime(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    Clock clock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }

    Sleeper sleeper() {
        return duration -> {
            sleeps.add(duration);
            now.updateAndGet(current -> current.plus(duration));
        };
    }

    void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    Instant now() {
        return now.get();
    }

    List<Duration> sleeps() {
        return List.copyOf(sleeps);
    }
}
