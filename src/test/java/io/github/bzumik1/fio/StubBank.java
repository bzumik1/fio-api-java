package io.github.bzumik1.fio;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.Instant;

final class StubBank implements BeforeEachCallback, AfterEachCallback {

    static final String SECRET = "aGEMtmwcsWAjPzhg3bPH3j7Iu15g56d66AdEbfIjqIgMR9Idh6u3";
    static final FioToken TOKEN = FioToken.of(SECRET);
    static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    private final Instant start;
    private StubFioServer server;
    private FakeTime time;
    private FioClient client;

    StubBank() {
        this(NOW);
    }

    StubBank(Instant start) {
        this.start = start;
    }

    static String printed(Throwable failure) {
        return Throwables.printed(failure);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        time = new FakeTime(start);
        server = StubFioServer.start(time);
        client = builder().build();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        server.close();
        if (server.mostRequestsInFlight() > 1) {
            throw new AssertionError("The bank got " + server.mostRequestsInFlight() + " requests at once");
        }
    }

    StubFioServer server() {
        return server;
    }

    FakeTime time() {
        return time;
    }

    FioClient client() {
        return client;
    }

    FioClient.Builder builder() {
        return FioClient.builder(TOKEN).baseUri(server.baseUri()).clock(time.clock()).sleeper(time.sleeper());
    }
}
