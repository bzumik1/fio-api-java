package io.github.bzumik1.fio;

import io.github.bzumik1.fio.StubFioServer.Hold;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.bzumik1.fio.Fixtures.PERIODS_STATEMENT;
import static io.github.bzumik1.fio.StubBank.SECRET;
import static io.github.bzumik1.fio.StubBank.printed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TransportFailureTest {

    private static final String URL_WITH_TOKEN = "https://fioapi.fio.cz/v1/rest/last/" + SECRET + "/transactions.json";

    @RegisterExtension
    final StubBank bank = new StubBank();

    private FioClient clientFailingWith(Exception failure) {
        return bank.builder().httpClient(FailingHttpClient.failingWith(failure)).build();
    }

    private static FioTransportException fetchFailure(FioClient client) {
        return catchThrowableOfType(FioTransportException.class, () -> client.transactions().sinceBookmark().fetch());
    }

    @Test
    void should_raise_transport_failure_that_never_reached_the_bank_when_downloading_given_connection_refused() throws IOException {
        int recentlyClosedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            recentlyClosedPort = socket.getLocalPort();
        }
        FioClient offline = bank.builder().baseUri(URI.create("http://127.0.0.1:" + recentlyClosedPort + "/v1/rest")).build();

        FioTransportException failure = fetchFailure(offline);

        assertThat(failure.mayHaveReachedTheBank()).isFalse();
        assertThat(failure.statusCode()).isEmpty();
        assertThat(failure.responseBody()).isEmpty();
        assertThat(failure).hasMessageContaining("/last/***/transactions.json");
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void should_give_up_within_request_timeout_when_downloading_given_body_that_stalls_after_the_headers() {
        bank.server().enqueueStallingAfterPartOfBody("{\"accountStatement\":");
        Duration requestTimeout = Duration.ofMillis(300);
        FioClient impatient = bank.builder().requestTimeout(requestTimeout).build();
        long started = System.nanoTime();

        FioTransportException failure = fetchFailure(impatient);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        assertThat(elapsed).isBetween(requestTimeout, requestTimeout.plusSeconds(5));
        assertThat(failure).hasMessageContaining("did not finish within PT0.3S").hasMessageNotContaining(SECRET);
        assertThat(failure.mayHaveReachedTheBank()).isTrue();
        assertThat(bank.server().requests()).hasSize(1);
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void should_raise_transport_failure_and_keep_interrupt_flag_when_interrupted_given_request_waiting_for_answer() throws Exception {
        bank.server().respondWith(200, Fixtures.read(PERIODS_STATEMENT));
        Hold hold = bank.server().holdNextRequest();
        AtomicReference<FioTransportException> failure = new AtomicReference<>();
        AtomicBoolean interruptFlagKept = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            failure.set(fetchFailure(bank.client()));
            interruptFlagKept.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        hold.awaitArrival();

        caller.interrupt();
        caller.join();

        assertThat(interruptFlagKept).isTrue();
        assertThat(failure.get()).hasMessageContaining("was interrupted").hasMessageNotContaining(SECRET);
        assertThat(failure.get().mayHaveReachedTheBank()).isTrue();
        assertThat(printed(failure.get())).contains("InterruptedException").doesNotContain(SECRET);
    }

    @Test
    void should_hide_token_in_message_and_causes_when_downloading_given_http_client_failure_naming_the_url() {
        IOException failureNamingUrl = new IOException("Cannot reach " + URL_WITH_TOKEN, new IOException("Nested " + SECRET));

        FioTransportException failure = fetchFailure(clientFailingWith(failureNamingUrl));

        assertThat(failure).hasMessageContaining("Cannot reach https://fioapi.fio.cz/v1/rest/last/***/transactions.json");
        assertThat(failure.getCause()).hasMessageContaining("Cannot reach").hasRootCauseMessage("java.io.IOException: Nested ***");
        assertThat(printed(failure)).doesNotContain(SECRET).contains("java.io.IOException");
    }

    @Test
    void should_hide_token_in_suppressed_failure_when_http_client_fails_given_suppressed_exception_naming_the_token() {
        IOException failureWithSuppressed = new IOException("Connection reset");
        failureWithSuppressed.addSuppressed(new IOException("Closing " + URL_WITH_TOKEN + " failed too"));

        FioTransportException failure = fetchFailure(clientFailingWith(failureWithSuppressed));

        assertThat(failure.getCause().getSuppressed()).singleElement()
                .satisfies(suppressed -> assertThat(suppressed).hasMessageContaining("/last/***/transactions.json"));
        assertThat(printed(failure)).doesNotContain(SECRET).contains("Suppressed:");
    }

    @Test
    void should_raise_masked_transport_failure_when_http_client_throws_given_runtime_exception_naming_the_token() {
        FioClient throwing = bank.builder()
                .httpClient(FailingHttpClient.throwingWhenSending(new IllegalStateException("Client for " + URL_WITH_TOKEN + " is closed")))
                .build();

        FioTransportException failure = fetchFailure(throwing);

        assertThat(failure.getClass()).isEqualTo(FioTransportException.class);
        assertThat(failure).hasMessageContaining("IllegalStateException: Client for https://fioapi.fio.cz/v1/rest/last/***/transactions.json");
        assertThat(failure.mayHaveReachedTheBank()).isTrue();
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_raise_transport_failure_that_never_reached_the_bank_when_downloading_given_connect_timeout() {
        FioTransportException failure = fetchFailure(clientFailingWith(new HttpConnectTimeoutException("connect timed out")));

        assertThat(failure.mayHaveReachedTheBank()).isFalse();
    }

    @Test
    void should_raise_transport_failure_that_may_have_reached_the_bank_when_downloading_given_connection_reset() {
        FioTransportException failure = fetchFailure(clientFailingWith(new IOException("Connection reset")));

        assertThat(failure.mayHaveReachedTheBank()).isTrue();
    }

    @Test
    void should_keep_original_cause_when_failing_given_cause_without_token() {
        IOException plainFailure = new IOException("Connection reset");

        FioTransportException failure = catchThrowableOfType(FioTransportException.class,
                () -> clientFailingWith(plainFailure).bookmark().moveAfterTransaction(1L));

        assertThat(failure.getCause()).isSameAs(plainFailure);
    }
}
