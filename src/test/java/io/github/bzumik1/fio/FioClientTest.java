package io.github.bzumik1.fio;

import io.github.bzumik1.fio.StubFioServer.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static io.github.bzumik1.fio.Fixtures.IMPORT_ACCEPTED;
import static io.github.bzumik1.fio.Fixtures.PERIODS_STATEMENT;
import static io.github.bzumik1.fio.StubBank.NOW;
import static io.github.bzumik1.fio.StubBank.SECRET;
import static io.github.bzumik1.fio.StubBank.TOKEN;
import static io.github.bzumik1.fio.StubBank.printed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

class FioClientTest {

    private static final String JSON = "application/json";
    private static final String XML = "application/xml";
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate TO = LocalDate.of(2026, 2, 9);
    private static final String LAST_STATEMENT = "/v1/rest/lastStatement/" + SECRET + "/statement";

    @RegisterExtension
    final StubBank bank = new StubBank();

    private StubFioServer server() {
        return bank.server();
    }

    private FioClient client() {
        return bank.client();
    }

    private static RecordingHttpClient recording() {
        return new RecordingHttpClient(200, new byte[0]);
    }

    @Test
    void should_download_json_after_bookmark_when_fetched_given_movements_since_bookmark() {
        server().respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        AccountStatement statement = client().transactions().sinceBookmark().fetch();

        assertThat(server().requests()).containsExactly(
                new RecordedRequest("GET", "/v1/rest/last/" + SECRET + "/transactions.json", JSON));
        assertThat(statement.info().account()).isEqualTo(DomesticAccount.parse("2400222222/2010"));
        assertThat(statement.transactions()).extracting(Transaction::id)
                .containsExactly(1148734530L, 1148734781L, 1149190193L);
    }

    @Test
    void should_put_iso_dates_into_path_when_fetched_given_period() {
        server().respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        AccountStatement statement = client().transactions().between(FROM, TO).fetch();

        assertThat(server().requests()).containsExactly(
                new RecordedRequest("GET", "/v1/rest/periods/" + SECRET + "/2026-01-05/2026-02-09/transactions.json", JSON));
        assertThat(statement.transactions()).hasSize(3);
    }

    @Test
    void should_download_single_day_when_period_fetched_given_same_start_and_end() {
        server().respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        client().transactions().between(LocalDate.of(2012, 7, 27), LocalDate.of(2012, 7, 27)).fetch();

        assertThat(server().requests()).extracting(RecordedRequest::path)
                .containsExactly("/v1/rest/periods/" + SECRET + "/2012-07-27/2012-07-27/transactions.json");
    }

    @Test
    void should_reject_without_request_when_period_chosen_given_end_before_start() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client().transactions().between(LocalDate.of(2012, 7, 28), LocalDate.of(2012, 7, 27)));
        assertThat(server().requests()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TransactionFormat.class)
    void should_download_with_format_extension_when_downloaded_given_period_and_raw_format(TransactionFormat format) {
        server().respondWith(200, "text/" + format.extension() + ";charset=windows-1250", "výpis".getBytes(StandardCharsets.UTF_8));

        Download document = client().transactions().between(FROM, TO).download(format);

        assertThat(server().requests()).containsExactly(new RecordedRequest(
                "GET", "/v1/rest/periods/" + SECRET + "/2026-01-05/2026-02-09/transactions." + format.extension(), format.mediaType()));
        assertThat(document.mediaType()).isEqualTo("text/" + format.extension() + ";charset=windows-1250");
        assertThat(document.content()).isEqualTo("výpis".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_download_with_format_extension_when_downloaded_given_movements_since_bookmark() {
        server().respondWith(200, "application/x-ofx", new byte[] {1, 2, 3});

        Download document = client().transactions().sinceBookmark().download(TransactionFormat.OFX);

        assertThat(server().requests()).extracting(RecordedRequest::path)
                .containsExactly("/v1/rest/last/" + SECRET + "/transactions.ofx");
        assertThat(document).isEqualTo(new Download("application/x-ofx", StandardCharsets.UTF_8, new byte[] {1, 2, 3}));
    }

    @Test
    void should_fall_back_to_octet_stream_when_downloaded_given_answer_without_content_type() {
        server().respondWith(200, "", new byte[] {7});

        Download document = client().transactions().sinceBookmark().download(TransactionFormat.GPC);

        assertThat(document.mediaType()).isEqualTo("application/octet-stream");
    }

    @Test
    void should_ask_bank_for_statement_number_when_id_asked_given_latest_statement() {
        server().respondWith(200, "text/plain;charset=UTF-8", "2026,10".getBytes(StandardCharsets.UTF_8));

        StatementId id = client().statements().latest().id();

        assertThat(id).isEqualTo(new StatementId(2026, 10));
        assertThat(server().requests()).containsExactly(new RecordedRequest("GET", LAST_STATEMENT, "text/plain"));
    }

    @Test
    void should_download_statement_thirty_seconds_after_asking_its_number_when_downloaded_given_latest_statement() {
        server().enqueue(200, "text/plain", "2026,10".getBytes(StandardCharsets.UTF_8));
        server().enqueue(200, "application/pdf", "%PDF".getBytes(StandardCharsets.UTF_8));

        Download pdf = client().statements().latest().download(StatementFormat.PDF);

        assertThat(server().requests()).extracting(RecordedRequest::path, RecordedRequest::accept).containsExactly(
                tuple(LAST_STATEMENT, "text/plain"),
                tuple("/v1/rest/by-id/" + SECRET + "/2026/10/transactions.pdf", "application/pdf"));
        assertThat(server().arrivals()).containsExactly(NOW, NOW.plusSeconds(30));
        assertThat(pdf.asText()).isEqualTo("%PDF");
    }

    @Test
    void should_ask_bank_for_number_once_when_id_fetch_and_download_called_given_one_latest_request() {
        server().route("lastStatement", 200, "text/plain", "2026,10".getBytes(StandardCharsets.UTF_8));
        server().route("transactions.json", 200, JSON, Fixtures.read(PERIODS_STATEMENT).getBytes(StandardCharsets.UTF_8));
        server().route("transactions.pdf", 200, "application/pdf", "%PDF".getBytes(StandardCharsets.UTF_8));
        FioClient.StatementRequest latest = client().statements().latest();

        latest.id();
        latest.fetch();
        latest.download(StatementFormat.PDF);

        assertThat(server().requests()).extracting(RecordedRequest::path).containsExactly(
                LAST_STATEMENT,
                "/v1/rest/by-id/" + SECRET + "/2026/10/transactions.json",
                "/v1/rest/by-id/" + SECRET + "/2026/10/transactions.pdf");
    }

    @Test
    void should_ask_bank_for_number_each_time_when_id_asked_given_latest_chosen_twice() {
        server().respondWith(200, "text/plain", "2026,10".getBytes(StandardCharsets.UTF_8));

        client().statements().latest().id();
        client().statements().latest().id();

        assertThat(server().requests()).extracting(RecordedRequest::path).containsExactly(LAST_STATEMENT, LAST_STATEMENT);
    }

    @Test
    void should_download_json_by_number_when_fetched_given_statement_chosen_by_number() {
        server().respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        AccountStatement statement = client().statements().number(2012, 1).fetch();

        assertThat(server().requests()).containsExactly(
                new RecordedRequest("GET", "/v1/rest/by-id/" + SECRET + "/2012/1/transactions.json", JSON));
        assertThat(statement.transactions()).hasSize(3);
    }

    @Test
    void should_return_number_without_request_when_id_asked_given_statement_chosen_by_number() {
        StatementId id = client().statements().number(new StatementId(2012, 1)).id();

        assertThat(id).isEqualTo(new StatementId(2012, 1));
        assertThat(server().requests()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(StatementFormat.class)
    void should_download_with_format_extension_when_downloaded_given_statement_chosen_by_number(StatementFormat format) {
        server().respondWith(200, format.mediaType(), new byte[] {1});

        client().statements().number(2026, 3).download(format);

        assertThat(server().requests()).containsExactly(new RecordedRequest(
                "GET", "/v1/rest/by-id/" + SECRET + "/2026/3/transactions." + format.extension(), format.mediaType()));
    }

    @Test
    void should_raise_generic_failure_when_latest_statement_requested_given_answer_that_is_not_a_number() {
        server().respondWith(200, "text/plain", "<html>Údržba</html>".getBytes(StandardCharsets.UTF_8));

        FioException failure = catchThrowableOfType(FioException.class, () -> client().statements().latest().id());

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure).hasMessageContaining("Not a statement number");
    }

    @Test
    void should_raise_generic_failure_with_ok_status_when_latest_statement_requested_given_statement_number_zero() {
        server().respondWith(200, "text/plain", "2026,0".getBytes(StandardCharsets.UTF_8));

        FioException failure = catchThrowableOfType(FioException.class, () -> client().statements().latest().id());

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure.statusCode()).hasValue(200);
        assertThat(failure.responseBody()).hasValue("2026,0");
    }

    @Test
    void should_move_bookmark_after_transaction_when_requested_given_empty_ok_answer() {
        server().respondWith(200, "");

        client().bookmark().moveAfterTransaction(1147608196L);

        assertThat(server().requests()).extracting(RecordedRequest::method, RecordedRequest::path)
                .containsExactly(tuple("GET", "/v1/rest/set-last-id/" + SECRET + "/1147608196/"));
    }

    @Test
    void should_move_bookmark_to_date_when_requested_given_empty_ok_answer() {
        server().respondWith(200, "");

        client().bookmark().moveToDate(LocalDate.of(2023, 7, 27));

        assertThat(server().requests()).extracting(RecordedRequest::method, RecordedRequest::path)
                .containsExactly(tuple("GET", "/v1/rest/set-last-date/" + SECRET + "/2023-07-27/"));
    }

    @Test
    void should_move_bookmark_when_requested_given_no_content_answer() {
        server().respondWith(204, "");

        assertThatCode(() -> client().bookmark().moveAfterTransaction(1)).doesNotThrowAnyException();
        assertThat(server().requests()).hasSize(1);
    }

    @Test
    void should_reject_without_request_when_bookmark_moved_given_negative_id() {
        assertThatIllegalArgumentException().isThrownBy(() -> client().bookmark().moveAfterTransaction(-1));
        assertThat(server().requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 304})
    void should_raise_generic_failure_keeping_status_when_downloading_given_redirect_or_not_modified(int status) {
        server().respondWith(status, "");

        FioException failure = catchThrowableOfType(FioException.class,
                () -> client().transactions().sinceBookmark().download(TransactionFormat.CSV));

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure.statusCode()).hasValue(status);
        assertThat(failure).hasMessageContaining("HTTP " + status).hasMessageNotContaining(SECRET);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 304})
    void should_raise_generic_failure_keeping_status_when_bookmark_moved_given_redirect_or_not_modified(int status) {
        server().respondWith(status, "");

        FioException failure = catchThrowableOfType(FioException.class, () -> client().bookmark().moveAfterTransaction(1));

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure.statusCode()).hasValue(status);
    }

    @Test
    void should_join_paths_once_when_built_given_base_uri_with_trailing_slash() {
        FioClient trailingSlashClient = bank.builder().baseUri(URI.create(server().baseUri() + "/")).build();
        server().respondWith(200, "");

        trailingSlashClient.bookmark().moveAfterTransaction(1);

        assertThat(server().requests()).extracting(RecordedRequest::path)
                .containsExactly("/v1/rest/set-last-id/" + SECRET + "/1/");
    }

    @Test
    void should_reject_when_base_uri_set_given_relative_uri() {
        FioClient.Builder builder = FioClient.builder(TOKEN);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.baseUri(URI.create("/v1/rest")))
                .withMessageContaining("absolute");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://fioapi.fio.cz/v1/rest", "http://192.168.1.10/v1/rest", "http://localhost.example/v1", "http:/v1/rest"})
    void should_reject_when_base_uri_set_given_plain_http_to_a_host_that_is_not_loopback(String uri) {
        FioClient.Builder builder = FioClient.builder(TOKEN);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.baseUri(URI.create(uri)))
                .withMessageContaining("https");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8089/v1/rest", "http://127.0.0.1:8089/v1/rest", "http://[::1]:8089/v1/rest", "https://fio.example/api"})
    void should_send_to_that_address_when_base_uri_set_given_https_or_loopback_uri(String uri) {
        RecordingHttpClient http = recording();
        FioClient pointed = FioClient.builder(TOKEN).baseUri(URI.create(uri)).httpClient(http).build();

        pointed.bookmark().moveAfterTransaction(7);

        assertThat(http.requests()).extracting(HttpRequest::uri)
                .containsExactly(URI.create(uri + "/set-last-id/" + SECRET + "/7/"));
    }

    @Test
    void should_send_to_production_api_when_built_given_default_base_uri() {
        RecordingHttpClient http = recording();

        FioClient.builder(TOKEN).httpClient(http).build().bookmark().moveAfterTransaction(7);

        assertThat(http.requests()).extracting(HttpRequest::uri)
                .containsExactly(URI.create("https://fioapi.fio.cz/v1/rest/set-last-id/" + SECRET + "/7/"));
    }

    @Test
    void should_give_each_request_two_minutes_when_built_given_default_request_timeout() {
        RecordingHttpClient http = recording();

        FioClient.builder(TOKEN).httpClient(http).build().bookmark().moveAfterTransaction(7);

        assertThat(http.requests()).extracting(HttpRequest::timeout).containsExactly(Optional.of(Duration.ofMinutes(2)));
    }

    @Test
    void should_give_each_request_the_configured_time_when_built_given_request_timeout() {
        RecordingHttpClient http = recording();

        FioClient.builder(TOKEN).httpClient(http).requestTimeout(Duration.ofSeconds(30)).build().bookmark().moveAfterTransaction(7);

        assertThat(http.requests()).extracting(HttpRequest::timeout).containsExactly(Optional.of(Duration.ofSeconds(30)));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void should_reject_when_request_timeout_set_given_duration_that_is_not_positive(long seconds) {
        FioClient.Builder builder = FioClient.builder(TOKEN);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.requestTimeout(Duration.ofSeconds(seconds)))
                .withMessageContaining("positive");
    }

    @Test
    void should_connect_within_ten_seconds_when_built_given_default_http_client() {
        assertThat(FioClient.builder(TOKEN).httpClientOrDefault().connectTimeout()).hasValue(Duration.ofSeconds(10));
    }

    @Test
    void should_use_given_http_client_when_built_given_own_http_client() {
        RecordingHttpClient http = recording();

        assertThat(FioClient.builder(TOKEN).httpClient(http).httpClientOrDefault()).isSameAs(http);
    }

    @Test
    void should_reject_when_message_locale_set_given_language_fio_does_not_speak() {
        FioClient.Builder builder = FioClient.builder(TOKEN);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.messageLocale(Locale.GERMAN))
                .withMessageContaining("de");
    }

    @ParameterizedTest
    @CsvSource({"cs, cs", "sk-SK, sk", "en-GB, en"})
    void should_ask_for_messages_in_that_language_when_uploading_given_message_locale(String languageTag, String lng) {
        server().respondWith(200, XML, Fixtures.read(IMPORT_ACCEPTED).getBytes(StandardCharsets.UTF_8));
        FioClient localised = bank.builder().messageLocale(Locale.forLanguageTag(languageTag)).build();

        localised.payments().upload(new PaymentFile(PaymentFileType.ABO, "UHL1".getBytes(StandardCharsets.US_ASCII)));

        assertThat(server().bodies()).singleElement().extracting(StubFioServer.RecordedBody::text).asString()
                .contains("name=\"lng\"\r\n\r\n" + lng + "\r\n");
    }

    @Test
    void should_ask_for_english_messages_when_uploading_given_default_message_locale() {
        server().respondWith(200, XML, Fixtures.read(IMPORT_ACCEPTED).getBytes(StandardCharsets.UTF_8));

        client().payments().upload(new PaymentFile(PaymentFileType.ABO, "UHL1".getBytes(StandardCharsets.US_ASCII)));

        assertThat(server().bodies()).singleElement().extracting(StubFioServer.RecordedBody::text).asString()
                .contains("name=\"lng\"\r\n\r\nen\r\n");
    }

    @Test
    void should_hide_token_when_printed_given_client() {
        assertThat(FioClient.builder(TOKEN).build()).hasToString("FioClient[FioToken[***]]");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 401, 403})
    void should_raise_authorization_failure_when_downloading_given_status_the_api_uses_for_a_bad_token(int status) {
        server().respondWith(status, "<html>Internal Server Error</html>");

        FioAuthorizationException failure = catchThrowableOfType(FioAuthorizationException.class,
                () -> client().transactions().sinceBookmark().fetch());

        assertThat(failure.statusCode()).hasValue(status);
        assertThat(failure.responseBody()).hasValue("<html>Internal Server Error</html>");
        assertThat(failure).hasMessageContaining("token").hasMessageNotContaining(SECRET);
    }

    @Test
    void should_raise_history_locked_naming_earliest_day_when_downloading_given_unprocessable_entity() {
        server().respondHistoryLocked();

        FioHistoryLockedException failure = catchThrowableOfType(FioHistoryLockedException.class,
                () -> client().transactions().between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)).fetch());

        assertThat(failure.statusCode()).hasValue(422);
        assertThat(failure.responseBody()).hasValue(StubFioServer.historyLockedBody(LocalDate.of(2026, 7, 19)));
        assertThat(failure).hasMessageContaining("90 days").hasMessageNotContaining(SECRET);
    }

    @Test
    void should_raise_too_many_transactions_when_downloading_given_payload_too_large() {
        server().respondWith(413, "");

        FioTooManyTransactionsException failure = catchThrowableOfType(FioTooManyTransactionsException.class,
                () -> client().transactions().between(FROM, TO).fetch());

        assertThat(failure.statusCode()).hasValue(413);
        assertThat(failure).hasMessageContaining("50000").hasMessageNotContaining(SECRET);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 503})
    void should_raise_generic_failure_with_status_and_body_when_downloading_given_other_error_status(int status) {
        server().respondWith(status, "Chyba");

        FioException failure = catchThrowableOfType(FioException.class, () -> client().transactions().sinceBookmark().fetch());

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure.statusCode()).hasValue(status);
        assertThat(failure.responseBody()).hasValue("Chyba");
        assertThat(failure).hasMessageContaining("HTTP " + status).hasMessageNotContaining(SECRET);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 409, 413, 422, 500})
    void should_mask_token_in_message_and_body_when_failing_given_error_page_that_echoes_the_url(int status) {
        FioClient failFast = bank.builder().rateLimit(RateLimit.failFast()).build();
        server().respondWith(status, "Not found: /v1/rest/last/" + SECRET + "/transactions.json");

        FioException failure = catchThrowableOfType(FioException.class, () -> failFast.transactions().sinceBookmark().fetch());

        assertThat(failure.statusCode()).hasValue(status);
        assertThat(failure).hasMessageContaining("/last/***/transactions.json");
        assertThat(failure.responseBody()).hasValue("Not found: /v1/rest/last/***/transactions.json");
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_raise_generic_failure_with_reason_when_downloading_given_ok_answer_that_is_not_a_statement() {
        server().respondWith(200, "<html>Údržba " + SECRET + "</html>");

        FioException failure = catchThrowableOfType(FioException.class, () -> client().transactions().sinceBookmark().fetch());

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure.statusCode()).hasValue(200);
        assertThat(failure.responseBody()).hasValue("<html>Údržba ***</html>");
        assertThat(failure).hasMessageContaining("not valid JSON").hasMessageNotContaining(SECRET);
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_permit_only_documented_failures_when_switched_over_given_sealed_hierarchy() {
        List<Class<?>> permitted = List.of(FioException.class.getPermittedSubclasses());

        assertThat(FioException.class.isSealed()).isTrue();
        assertThat(permitted).containsExactlyInAnyOrder(
                FioRateLimitException.class,
                FioAuthorizationException.class,
                FioHistoryLockedException.class,
                FioTooManyTransactionsException.class,
                FioTransportException.class);
    }
}
