package io.github.bzumik1.fio;

import io.github.bzumik1.fio.StubFioServer.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static io.github.bzumik1.fio.Fixtures.PERIODS_STATEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

class FioClientTest {

    private static final String SECRET = "aGEMtmwcsWAjPzhg3bPH3j7Iu15g56d66AdEbfIjqIgMR9Idh6u3";
    private static final FioToken TOKEN = FioToken.of(SECRET);
    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");
    private static final String JSON = "application/json";
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate TO = LocalDate.of(2026, 2, 9);

    private StubFioServer server;
    private FakeTime time;
    private FioClient client;

    @BeforeEach
    void startServer() {
        server = StubFioServer.start();
        time = new FakeTime(NOW);
        client = builder().build();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private FioClient.Builder builder() {
        return FioClient.builder(TOKEN).baseUri(server.baseUri()).clock(time.clock()).sleeper(time.sleeper());
    }

    @Test
    void should_download_since_bookmark_as_json_when_fetched() {
        server.respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        AccountStatement statement = client.transactions().sinceBookmark().fetch();

        assertThat(server.requests()).containsExactly(
                new RecordedRequest("GET", "/v1/rest/last/" + SECRET + "/transactions.json", JSON));
        assertThat(statement.info().account()).isEqualTo(DomesticAccount.parse("2400222222/2010"));
        assertThat(statement.transactions()).extracting(Transaction::id)
                .containsExactly(1148734530L, 1148734781L, 1149190193L);
    }

    @Test
    void should_put_iso_dates_into_path_when_period_fetched() {
        server.respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        AccountStatement statement = client.transactions().between(FROM, TO).fetch();

        assertThat(server.requests()).containsExactly(
                new RecordedRequest("GET", "/v1/rest/periods/" + SECRET + "/2026-01-05/2026-02-09/transactions.json", JSON));
        assertThat(statement.transactions()).hasSize(3);
    }

    @Test
    void should_download_single_day_when_period_fetched_given_same_start_and_end() {
        server.respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        client.transactions().between(LocalDate.of(2012, 7, 27), LocalDate.of(2012, 7, 27)).fetch();

        assertThat(server.requests()).extracting(RecordedRequest::path)
                .containsExactly("/v1/rest/periods/" + SECRET + "/2012-07-27/2012-07-27/transactions.json");
    }

    @Test
    void should_reject_without_request_when_period_chosen_given_end_before_start() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.transactions().between(LocalDate.of(2012, 7, 28), LocalDate.of(2012, 7, 27)));
        assertThat(server.requests()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TransactionFormat.class)
    void should_download_raw_format_with_its_extension_when_period_downloaded(TransactionFormat format) {
        server.respondWith(200, "text/" + format.extension() + ";charset=windows-1250", "výpis".getBytes(StandardCharsets.UTF_8));

        Download document = client.transactions().between(FROM, TO).download(format);

        assertThat(server.requests()).containsExactly(new RecordedRequest(
                "GET", "/v1/rest/periods/" + SECRET + "/2026-01-05/2026-02-09/transactions." + format.extension(), format.mediaType()));
        assertThat(document.mediaType()).isEqualTo("text/" + format.extension() + ";charset=windows-1250");
        assertThat(document.content()).isEqualTo("výpis".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_download_raw_format_since_bookmark_when_downloaded() {
        server.respondWith(200, "application/x-ofx", new byte[] {1, 2, 3});

        Download document = client.transactions().sinceBookmark().download(TransactionFormat.OFX);

        assertThat(server.requests()).extracting(RecordedRequest::path)
                .containsExactly("/v1/rest/last/" + SECRET + "/transactions.ofx");
        assertThat(document).isEqualTo(new Download("application/x-ofx", StandardCharsets.UTF_8, new byte[] {1, 2, 3}));
    }

    @Test
    void should_fall_back_to_octet_stream_when_downloaded_given_answer_without_content_type() {
        server.respondWith(200, "", new byte[] {7});

        Download document = client.transactions().sinceBookmark().download(TransactionFormat.GPC);

        assertThat(document.mediaType()).isEqualTo("application/octet-stream");
    }

    @Test
    void should_ask_for_latest_statement_number_when_latest_statement_id_requested() {
        server.respondWith(200, "text/plain;charset=UTF-8", "2026,10".getBytes(StandardCharsets.UTF_8));

        StatementId id = client.statements().latest().id();

        assertThat(id).isEqualTo(new StatementId(2026, 10));
        assertThat(server.requests()).containsExactly(
                new RecordedRequest("GET", "/v1/rest/lastStatement/" + SECRET + "/statement", "text/plain"));
    }

    @Test
    void should_ask_number_first_then_download_statement_when_latest_statement_downloaded() {
        server.enqueue(200, "text/plain", "2026,10".getBytes(StandardCharsets.UTF_8));
        server.enqueue(200, "application/pdf", "%PDF".getBytes(StandardCharsets.UTF_8));

        Download pdf = client.statements().latest().download(StatementFormat.PDF);

        assertThat(server.requests()).extracting(RecordedRequest::path, RecordedRequest::accept).containsExactly(
                tuple("/v1/rest/lastStatement/" + SECRET + "/statement", "text/plain"),
                tuple("/v1/rest/by-id/" + SECRET + "/2026/10/transactions.pdf", "application/pdf"));
        assertThat(pdf.asText()).isEqualTo("%PDF");
        assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void should_download_statement_by_number_as_json_when_fetched() {
        server.respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        AccountStatement statement = client.statements().number(2012, 1).fetch();

        assertThat(server.requests()).containsExactly(
                new RecordedRequest("GET", "/v1/rest/by-id/" + SECRET + "/2012/1/transactions.json", JSON));
        assertThat(statement.transactions()).hasSize(3);
        assertThat(client.statements().number(new StatementId(2012, 1)).id()).isEqualTo(new StatementId(2012, 1));
    }

    @ParameterizedTest
    @EnumSource(StatementFormat.class)
    void should_download_statement_in_raw_format_when_downloaded(StatementFormat format) {
        server.respondWith(200, format.mediaType(), new byte[] {1});

        client.statements().number(2026, 3).download(format);

        assertThat(server.requests()).containsExactly(new RecordedRequest(
                "GET", "/v1/rest/by-id/" + SECRET + "/2026/3/transactions." + format.extension(), format.mediaType()));
    }

    @Test
    void should_raise_generic_failure_when_latest_statement_requested_given_answer_that_is_not_a_number() {
        server.respondWith(200, "text/plain", "<html>Údržba</html>".getBytes(StandardCharsets.UTF_8));

        FioException failure = catchThrowableOfType(FioException.class, () -> client.statements().latest().id());

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure).hasMessageContaining("Not a statement number");
    }

    @Test
    void should_move_bookmark_after_transaction_when_requested_given_empty_ok_answer() {
        server.respondWith(200, "");

        client.bookmark().moveAfterTransaction(1147608196L);

        assertThat(server.requests()).extracting(RecordedRequest::method, RecordedRequest::path)
                .containsExactly(tuple("GET", "/v1/rest/set-last-id/" + SECRET + "/1147608196/"));
    }

    @Test
    void should_move_bookmark_to_date_when_requested_given_empty_ok_answer() {
        server.respondWith(200, "");

        client.bookmark().moveToDate(LocalDate.of(2023, 7, 27));

        assertThat(server.requests()).extracting(RecordedRequest::method, RecordedRequest::path)
                .containsExactly(tuple("GET", "/v1/rest/set-last-date/" + SECRET + "/2023-07-27/"));
    }

    @Test
    void should_reject_without_request_when_bookmark_moved_given_negative_id() {
        assertThatIllegalArgumentException().isThrownBy(() -> client.bookmark().moveAfterTransaction(-1));
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_join_paths_once_when_built_given_base_uri_with_trailing_slash() {
        FioClient trailingSlashClient = builder().baseUri(URI.create(server.baseUri() + "/")).build();
        server.respondWith(200, "");

        trailingSlashClient.bookmark().moveAfterTransaction(1);

        assertThat(server.requests()).extracting(RecordedRequest::path)
                .containsExactly("/v1/rest/set-last-id/" + SECRET + "/1/");
    }

    @Test
    void should_reject_when_building_given_invalid_settings() {
        FioClient.Builder builder = FioClient.builder(TOKEN);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.baseUri(URI.create("/v1/rest")));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.requestTimeout(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.requestTimeout(Duration.ofSeconds(-1)));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.messageLocale(Locale.GERMAN));
    }

    @Test
    void should_accept_czech_slovak_and_english_when_building_given_message_locale() {
        for (Locale locale : List.of(Locale.forLanguageTag("cs"), Locale.forLanguageTag("sk-SK"), Locale.UK)) {
            assertThat(FioClient.builder(TOKEN).messageLocale(locale).build()).isNotNull();
        }
    }

    @Test
    void should_build_with_defaults_when_built_given_only_token() {
        FioClient defaultClient = FioClient.builder(TOKEN).build();

        assertThat(defaultClient).hasToString("FioClient[FioToken[***]]");
        assertThat(FioClient.DEFAULT_BASE_URI).isEqualTo(URI.create("https://fioapi.fio.cz/v1/rest"));
        assertThat(FioClient.MINIMUM_REQUEST_INTERVAL).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void should_space_requests_thirty_seconds_apart_when_called_twice_given_default_rate_limit() {
        server.respondWith(200, "");

        client.bookmark().moveAfterTransaction(1);
        client.bookmark().moveAfterTransaction(2);

        assertThat(server.requests()).hasSize(2);
        assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void should_retry_after_thirty_seconds_when_bank_refuses_given_default_rate_limit() {
        server.enqueue(409, "");
        server.respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        AccountStatement statement = client.transactions().sinceBookmark().fetch();

        assertThat(statement.transactions()).hasSize(3);
        assertThat(server.requests()).hasSize(2);
        assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void should_raise_rate_limit_with_retry_time_when_bank_refuses_given_fail_fast() {
        FioClient failFast = builder().rateLimit(RateLimit.failFast()).build();
        server.respondWith(409, "");

        FioRateLimitException failure = catchThrowableOfType(FioRateLimitException.class,
                () -> failFast.transactions().sinceBookmark().fetch());

        assertThat(failure.statusCode()).hasValue(409);
        assertThat(failure.retryAfter()).isEqualTo(NOW.plusSeconds(30));
        assertThat(failure).hasMessageContaining("30 seconds").hasMessageNotContaining(SECRET);
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void should_raise_rate_limit_without_request_when_called_too_soon_given_fail_fast() {
        FioClient failFast = builder().rateLimit(RateLimit.failFast()).build();
        server.respondWith(200, "");
        failFast.bookmark().moveAfterTransaction(1);
        time.advance(Duration.ofSeconds(10));

        FioRateLimitException failure = catchThrowableOfType(FioRateLimitException.class,
                () -> failFast.bookmark().moveAfterTransaction(2));

        assertThat(failure.retryAfter()).isEqualTo(NOW.plusSeconds(30));
        assertThat(failure.responseBody()).isEmpty();
        assertThat(failure).hasMessageContaining("was not sent").hasMessageNotContaining(SECRET);
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void should_send_again_when_called_after_thirty_seconds_given_fail_fast() {
        FioClient failFast = builder().rateLimit(RateLimit.failFast()).build();
        server.respondWith(200, "");
        failFast.bookmark().moveAfterTransaction(1);
        time.advance(Duration.ofSeconds(30));

        failFast.bookmark().moveAfterTransaction(2);

        assertThat(server.requests()).hasSize(2);
    }

    @Test
    void should_give_up_when_bank_keeps_refusing_given_maximum_wait_exhausted() {
        FioClient patient = builder().rateLimit(RateLimit.waitUpTo(Duration.ofSeconds(70))).build();
        server.respondWith(409, "");

        assertThatThrownBy(() -> patient.bookmark().moveAfterTransaction(1)).isInstanceOf(FioRateLimitException.class);

        assertThat(server.requests()).hasSize(3);
        assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(30), Duration.ofSeconds(30));
    }

    @Test
    void should_raise_transport_failure_and_keep_interrupt_flag_when_waiting_for_slot_given_interrupted_thread() {
        FioClient interruptible = builder().sleeper(duration -> {
            throw new InterruptedException();
        }).build();
        server.respondWith(200, "");
        interruptible.bookmark().moveAfterTransaction(1);

        try {
            FioTransportException failure = catchThrowableOfType(FioTransportException.class,
                    () -> interruptible.bookmark().moveAfterTransaction(2));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure).hasMessageContaining("interrupted while waiting").hasMessageNotContaining(SECRET);
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 401, 403})
    void should_raise_authorization_failure_when_downloading_given_status_the_api_uses_for_a_bad_token(int status) {
        server.respondWith(status, "<html>Internal Server Error</html>");

        FioAuthorizationException failure = catchThrowableOfType(FioAuthorizationException.class,
                () -> client.transactions().sinceBookmark().fetch());

        assertThat(failure.statusCode()).hasValue(status);
        assertThat(failure.responseBody()).hasValue("<html>Internal Server Error</html>");
        assertThat(failure).hasMessageContaining("token").hasMessageNotContaining(SECRET);
    }

    @Test
    void should_raise_history_locked_when_downloading_given_unprocessable_entity() {
        server.respondWith(422, "Data není možné poskytnout bez silné autorizace.");

        FioHistoryLockedException failure = catchThrowableOfType(FioHistoryLockedException.class,
                () -> client.transactions().between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)).fetch());

        assertThat(failure.statusCode()).hasValue(422);
        assertThat(failure.responseBody()).hasValue("Data není možné poskytnout bez silné autorizace.");
        assertThat(failure).hasMessageContaining("90 days").hasMessageNotContaining(SECRET);
    }

    @Test
    void should_raise_too_many_transactions_when_downloading_given_payload_too_large() {
        server.respondWith(413, "");

        FioTooManyTransactionsException failure = catchThrowableOfType(FioTooManyTransactionsException.class,
                () -> client.transactions().between(FROM, TO).fetch());

        assertThat(failure.statusCode()).hasValue(413);
        assertThat(failure).hasMessageContaining("50000").hasMessageNotContaining(SECRET);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 503})
    void should_raise_generic_failure_with_status_and_body_when_downloading_given_other_error_status(int status) {
        server.respondWith(status, "Chyba");

        FioException failure = catchThrowableOfType(FioException.class, () -> client.transactions().sinceBookmark().fetch());

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure.statusCode()).hasValue(status);
        assertThat(failure.responseBody()).hasValue("Chyba");
        assertThat(failure).hasMessageContaining("HTTP " + status).hasMessageNotContaining(SECRET);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 413, 422, 500})
    void should_mask_token_in_message_and_body_when_failing_given_error_page_that_echoes_the_url(int status) {
        server.respondWith(status, "Not found: /v1/rest/last/" + SECRET + "/transactions.json");

        FioException failure = catchThrowableOfType(FioException.class, () -> client.transactions().sinceBookmark().fetch());

        assertThat(failure).hasMessageContaining("/last/***/transactions.json");
        assertThat(failure.responseBody()).hasValue("Not found: /v1/rest/last/***/transactions.json");
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_raise_generic_failure_with_reason_when_downloading_given_ok_answer_that_is_not_a_statement() {
        server.respondWith(200, "<html>Údržba " + SECRET + "</html>");

        FioException failure = catchThrowableOfType(FioException.class, () -> client.transactions().sinceBookmark().fetch());

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure.statusCode()).hasValue(200);
        assertThat(failure.responseBody()).hasValue("<html>Údržba ***</html>");
        assertThat(failure).hasMessageContaining("not valid JSON").hasMessageNotContaining(SECRET);
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_raise_transport_failure_without_token_when_downloading_given_server_that_is_not_listening() {
        URI unreachable = server.baseUri();
        server.close();
        FioClient offlineClient = builder().baseUri(unreachable).build();

        FioTransportException failure = catchThrowableOfType(FioTransportException.class,
                () -> offlineClient.transactions().sinceBookmark().fetch());

        assertThat(failure.statusCode()).isEmpty();
        assertThat(failure.responseBody()).isEmpty();
        assertThat(failure).hasMessageContaining("/last/***/transactions.json");
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_raise_transport_failure_when_downloading_given_answer_slower_than_request_timeout() {
        server.respondAfter(Duration.ofSeconds(2), 200, Fixtures.read(PERIODS_STATEMENT));
        FioClient impatientClient = builder().requestTimeout(Duration.ofMillis(200)).build();

        assertThatThrownBy(() -> impatientClient.transactions().sinceBookmark().fetch())
                .isInstanceOf(FioTransportException.class);
    }

    @Test
    void should_send_through_given_http_client_and_hide_token_in_cause_when_it_fails_with_message_naming_the_url() {
        IOException failureNamingUrl = new IOException("Cannot reach https://fioapi.fio.cz/v1/rest/last/" + SECRET + "/transactions.json",
                new IOException("Nested " + SECRET));
        FioClient failingClient = builder().httpClient(new FailingHttpClient(failureNamingUrl)).build();

        FioTransportException failure = catchThrowableOfType(FioTransportException.class,
                () -> failingClient.transactions().sinceBookmark().fetch());

        assertThat(failure).hasMessageContaining("Cannot reach https://fioapi.fio.cz/v1/rest/last/***/transactions.json");
        assertThat(failure.getCause()).isNotNull();
        assertThat(printed(failure)).doesNotContain(SECRET).contains("java.io.IOException");
    }

    @Test
    void should_keep_original_cause_when_failing_given_cause_without_token() {
        IOException plainFailure = new IOException("Connection reset");
        FioClient failingClient = builder().httpClient(new FailingHttpClient(plainFailure)).build();

        FioTransportException failure = catchThrowableOfType(FioTransportException.class,
                () -> failingClient.bookmark().moveAfterTransaction(1L));

        assertThat(failure.getCause()).isSameAs(plainFailure);
    }

    @Test
    void should_raise_transport_failure_and_keep_interrupt_flag_when_downloading_given_interrupted_request() {
        server.respondAfter(Duration.ofSeconds(5), 200, Fixtures.read(PERIODS_STATEMENT));
        FioClient failingClient = client;
        Thread.currentThread().interrupt();

        try {
            FioTransportException failure = catchThrowableOfType(FioTransportException.class,
                    () -> failingClient.transactions().sinceBookmark().fetch());

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure).hasMessageContaining("was interrupted");
            assertThat(printed(failure)).doesNotContain(SECRET).contains("InterruptedException");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void should_let_callers_switch_exhaustively_given_sealed_failures() {
        List<Class<?>> permitted = List.of(FioException.class.getPermittedSubclasses());

        assertThat(FioException.class.isSealed()).isTrue();
        assertThat(RuntimeException.class).isAssignableFrom(FioException.class);
        assertThat(permitted).containsExactlyInAnyOrder(
                FioRateLimitException.class,
                FioAuthorizationException.class,
                FioHistoryLockedException.class,
                FioTooManyTransactionsException.class,
                FioTransportException.class);
    }

    private static String printed(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
