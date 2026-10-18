package io.github.bzumik1.fio;

import io.github.bzumik1.fio.StubFioServer.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static io.github.bzumik1.fio.Fixtures.IMPORT_ACCEPTED;
import static io.github.bzumik1.fio.Fixtures.PERIODS_STATEMENT;
import static io.github.bzumik1.fio.StubBank.NOW;
import static io.github.bzumik1.fio.StubBank.SECRET;
import static io.github.bzumik1.fio.StubBank.TOKEN;
import static io.github.bzumik1.fio.StubBank.printed;
import static io.github.bzumik1.fio.Threads.awaitWaitingForLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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

        StatementId id = client().statements().latest().statementId();

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

        latest.statementId();
        latest.fetch();
        latest.download(StatementFormat.PDF);

        assertThat(server().requests()).extracting(RecordedRequest::path).containsExactly(
                LAST_STATEMENT,
                "/v1/rest/by-id/" + SECRET + "/2026/10/transactions.json",
                "/v1/rest/by-id/" + SECRET + "/2026/10/transactions.pdf");
    }

    @Test
    void should_fail_first_and_fetch_on_the_same_request_after_the_slot_when_fetched_given_latest_and_fail_fast() {
        server().route("lastStatement", 200, "text/plain", "2026,10".getBytes(StandardCharsets.UTF_8));
        server().route("transactions.json", 200, JSON, Fixtures.read(PERIODS_STATEMENT).getBytes(StandardCharsets.UTF_8));
        FioClient.StatementRequest latest = bank.builder().rateLimit(RateLimit.failFast()).build().statements().latest();

        FioRateLimitException refused = catchThrowableOfType(FioRateLimitException.class, latest::fetch);
        bank.time().advance(Duration.between(bank.time().now(), refused.retryAfter()));
        latest.fetch();

        assertThat(refused.statusCode()).isEmpty();
        assertThat(server().requests()).extracting(RecordedRequest::path).containsExactly(
                LAST_STATEMENT, "/v1/rest/by-id/" + SECRET + "/2026/10/transactions.json");
    }

    @Test
    void should_keep_message_and_body_short_when_id_asked_given_20_kib_page_instead_of_a_statement_number() {
        server().respondWith(200, "text/html", ("<html>" + "x".repeat(20 * 1024) + "</html>").getBytes(StandardCharsets.UTF_8));

        FioMalformedResponseException failure = catchThrowableOfType(FioMalformedResponseException.class,
                () -> client().statements().latest().statementId());

        assertThat(failure.getMessage()).hasSizeLessThan(17_000)
                .contains("Not a statement number: '<html>xxx", "x\u2026'");
        assertThat(failure.responseBody()).hasValueSatisfying(body -> assertThat(body).hasSize(FioException.RESPONSE_BODY_LIMIT + 1));
        assertThat(printed(failure)).hasSizeLessThan(40_000);
    }

    @Test
    void should_keep_every_cause_short_when_fetched_given_movement_id_of_20_kib_digits() {
        server().respondWith(200, JSON, Fixtures.read(PERIODS_STATEMENT).replace("1148734530", "\"" + "1".repeat(20 * 1024) + "\"")
                .getBytes(StandardCharsets.UTF_8));

        FioMalformedResponseException failure = catchThrowableOfType(FioMalformedResponseException.class,
                () -> client().transactions().sinceBookmark().fetch());

        assertThat(failure.getMessage()).hasSizeLessThan(1_000).contains("is not a whole number: '111", "1\u2026'");
        assertThat(printed(failure)).hasSizeLessThan(40_000);
    }

    @Test
    void should_ask_bank_again_when_id_asked_given_first_lookup_failed() {
        server().enqueue(500, "");
        server().enqueue(200, "text/plain", "2026,10".getBytes(StandardCharsets.UTF_8));
        FioClient.StatementRequest latest = client().statements().latest();

        assertThatThrownBy(latest::statementId).isInstanceOf(FioAuthorizationException.class);
        assertThat(latest.statementId()).isEqualTo(new StatementId(2026, 10));

        assertThat(server().requests()).extracting(RecordedRequest::path).containsExactly(LAST_STATEMENT, LAST_STATEMENT);
        assertThat(server().arrivals()).containsExactly(NOW, NOW.plusSeconds(30));
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void should_ask_bank_once_when_id_asked_concurrently_given_one_latest_request() throws Exception {
        server().respondWith(200, "text/plain", "2026,10".getBytes(StandardCharsets.UTF_8));
        StubFioServer.Hold hold = server().holdNextRequest();
        FioClient.StatementRequest latest = client().statements().latest();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<StatementId> first = pool.submit(latest::statementId);
            hold.awaitArrival();
            List<Thread> waiting = new CopyOnWriteArrayList<>();
            Future<StatementId> second = pool.submit(() -> {
                waiting.add(Thread.currentThread());
                return latest.statementId();
            });
            awaitWaitingForLock(waiting, List.of(second));
            assertThat(second).isNotDone();
            hold.release();

            assertThat(first.get()).isEqualTo(new StatementId(2026, 10));
            assertThat(second.get()).isEqualTo(new StatementId(2026, 10));
        } finally {
            pool.shutdownNow();
        }
        assertThat(server().requests()).extracting(RecordedRequest::path).containsExactly(LAST_STATEMENT);
    }

    @Test
    void should_ask_bank_for_number_each_time_when_id_asked_given_latest_chosen_twice() {
        server().respondWith(200, "text/plain", "2026,10".getBytes(StandardCharsets.UTF_8));

        client().statements().latest().statementId();
        client().statements().latest().statementId();

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
        StatementId id = client().statements().number(new StatementId(2012, 1)).statementId();

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
    void should_raise_malformed_response_when_latest_statement_requested_given_answer_that_is_not_a_number() {
        server().respondWith(200, "text/plain", "<html>Údržba</html>".getBytes(StandardCharsets.UTF_8));

        FioException failure = catchThrowableOfType(FioException.class, () -> client().statements().latest().statementId());

        assertThat(failure).isExactlyInstanceOf(FioMalformedResponseException.class)
                .hasMessageContaining("Not a statement number");
    }

    @Test
    void should_raise_malformed_response_with_ok_status_when_latest_statement_requested_given_statement_number_zero() {
        server().respondWith(200, "text/plain", "2026,0".getBytes(StandardCharsets.UTF_8));

        FioException failure = catchThrowableOfType(FioException.class, () -> client().statements().latest().statementId());

        assertThat(failure).isExactlyInstanceOf(FioMalformedResponseException.class);
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

    @Test
    void should_move_bookmark_to_the_start_when_bookmark_moved_given_id_zero() {
        server().respondWith(200, "");

        client().bookmark().moveAfterTransaction(0);

        assertThat(server().requests()).extracting(RecordedRequest::path).containsExactly("/v1/rest/set-last-id/" + SECRET + "/0/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+10000-01-01", "0000-12-31", "-0005-01-01"})
    void should_reject_without_request_when_period_or_day_chosen_given_year_outside_one_to_9999(String day) {
        LocalDate outside = LocalDate.parse(day);
        LocalDate inside = LocalDate.of(2026, 1, 1);

        assertThatIllegalArgumentException().isThrownBy(() -> client().transactions().between(outside, outside)).withMessageContaining("1 to 9999");
        assertThatIllegalArgumentException().isThrownBy(() -> client().transactions().between(inside, outside)).withMessageContaining("1 to 9999");
        assertThatIllegalArgumentException().isThrownBy(() -> client().merchant().between(outside, outside)).withMessageContaining("1 to 9999");
        assertThatIllegalArgumentException().isThrownBy(() -> client().bookmark().moveToDate(outside)).withMessageContaining("1 to 9999");
        assertThat(server().requests()).isEmpty();
    }

    @Test
    void should_send_the_first_and_last_day_when_period_chosen_given_years_one_and_9999() {
        server().respondWith(200, Fixtures.read(PERIODS_STATEMENT));

        client().transactions().between(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)).fetch();

        assertThat(server().requests()).extracting(RecordedRequest::path)
                .containsExactly("/v1/rest/periods/" + SECRET + "/0001-01-01/9999-12-31/transactions.json");
    }

    @Test
    void should_send_the_day_when_bookmark_moved_given_years_one_and_9999() {
        server().respondWith(200, "");

        client().bookmark().moveToDate(LocalDate.of(1, 1, 1));
        client().bookmark().moveToDate(LocalDate.of(9999, 12, 31));

        assertThat(server().requests()).extracting(RecordedRequest::path).containsExactly(
                "/v1/rest/set-last-date/" + SECRET + "/0001-01-01/",
                "/v1/rest/set-last-date/" + SECRET + "/9999-12-31/");
    }

    @Test
    void should_reject_with_parameter_name_when_bookmark_moved_given_null_day() {
        assertThatNullPointerException().isThrownBy(() -> client().bookmark().moveToDate(null)).withMessage("date");
    }

    @Test
    void should_raise_too_large_failure_without_reading_when_downloading_given_answer_longer_than_the_limit() {
        FioClient limited = bank.builder().maximumResponseSize(1024).build();
        server().respondWith(200, "application/pdf", new byte[4096]);

        FioException failure = catchThrowableOfType(FioException.class,
                () -> limited.statements().number(2026, 1).download(StatementFormat.PDF));

        assertThat(failure).isExactlyInstanceOf(FioResponseTooLargeException.class)
                .hasMessageContaining("more than 1024 bytes")
                .hasMessageNotContaining(SECRET);
        assertThat(((FioResponseTooLargeException) failure).limit()).isEqualTo(1024);
        assertThat(failure.mayHaveTakenEffect()).isTrue();
        assertThat(failure.statusCode()).isEmpty();
        assertThat(failure.responseBody()).isEmpty();
        assertThat(failure.getCause()).hasMessageContaining("longer than 1024 bytes");
    }

    @Test
    void should_read_the_whole_answer_when_downloading_given_answer_exactly_at_the_limit() {
        FioClient limited = bank.builder().maximumResponseSize(4096).build();
        server().respondWith(200, "application/pdf", new byte[4096]);

        Download pdf = limited.statements().number(2026, 1).download(StatementFormat.PDF);

        assertThat(pdf.content()).hasSize(4096);
    }

    @Test
    void should_raise_too_large_failure_when_downloading_given_answer_one_byte_over_the_limit() {
        FioClient limited = bank.builder().maximumResponseSize(4096).build();
        server().respondWith(200, "application/pdf", new byte[4097]);

        FioException failure = catchThrowableOfType(FioException.class,
                () -> limited.statements().number(2026, 1).download(StatementFormat.PDF));

        assertThat(failure).isExactlyInstanceOf(FioResponseTooLargeException.class).hasMessageContaining("more than 4096 bytes");
    }

    @Test
    void should_raise_too_large_failure_when_downloading_given_answer_over_the_limit_in_several_parts() {
        RecordingHttpClient http = RecordingHttpClient.answeringInParts(200, List.of(new byte[600], new byte[600], new byte[600]));
        FioClient limited = bank.builder().httpClient(http).maximumResponseSize(1024).build();

        FioException failure = catchThrowableOfType(FioException.class,
                () -> limited.statements().number(2026, 1).download(StatementFormat.PDF));

        assertThat(failure).isExactlyInstanceOf(FioResponseTooLargeException.class).hasMessageContaining("more than 1024 bytes");
    }

    @Test
    void should_fail_without_reading_the_body_when_downloading_given_content_length_over_the_limit() {
        RecordingHttpClient http = RecordingHttpClient.answeringWithContentLength(200, 1025, new byte[10]);
        FioClient limited = bank.builder().httpClient(http).maximumResponseSize(1024).build();

        FioException failure = catchThrowableOfType(FioException.class,
                () -> limited.statements().number(2026, 1).download(StatementFormat.PDF));

        assertThat(failure).isExactlyInstanceOf(FioResponseTooLargeException.class).hasMessageContaining("more than 1024 bytes");
        assertThat(http.deliveredBytes()).isZero();
    }

    @Test
    void should_read_the_whole_answer_when_downloading_given_content_length_at_the_limit() {
        RecordingHttpClient http = RecordingHttpClient.answeringWithContentLength(200, 1024, new byte[1024]);
        FioClient limited = bank.builder().httpClient(http).maximumResponseSize(1024).build();

        Download pdf = limited.statements().number(2026, 1).download(StatementFormat.PDF);

        assertThat(pdf.content()).hasSize(1024);
    }

    @Test
    void should_reject_when_maximum_response_size_set_given_size_not_positive() {
        assertThatThrownBy(() -> bank.builder().maximumResponseSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The maximum response size must be positive: 0");
    }

    @Test
    void should_raise_too_large_failure_when_downloading_given_default_builder_and_answer_over_256_mib() {
        byte[] mebibyte = new byte[1024 * 1024];
        List<byte[]> parts = new ArrayList<>(Collections.nCopies(256, mebibyte));
        parts.add(new byte[1]);
        RecordingHttpClient http = RecordingHttpClient.answeringInParts(200, parts);

        FioException failure = catchThrowableOfType(FioException.class,
                () -> bank.builder().httpClient(http).build().statements().number(2026, 1).download(StatementFormat.PDF));

        assertThat(failure).isExactlyInstanceOf(FioResponseTooLargeException.class)
                .hasMessageContaining("more than " + 256 * 1024 * 1024 + " bytes");
        assertThat(((FioResponseTooLargeException) failure).limit()).isEqualTo(256 * 1024 * 1024);
    }

    @Test
    void should_sleep_on_the_thread_when_built_given_default_sleeper() {
        assertThat(FioClient.builder(TOKEN).sleeper()).isSameAs(Sleeper.THREAD);
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
    @ValueSource(strings = {
            "http://fioapi.fio.cz/v1/rest", "http://192.168.1.10/v1/rest", "http://localhost.example/v1", "http://localhost:8089/v1",
            "http://127.0.0.2/v1", "http://LOCALHOST/v1", "http://0.0.0.0/v1", "HTTP://fioapi.fio.cz/v1/rest"})
    void should_refuse_to_build_naming_the_ways_out_when_built_given_plain_http_to_a_host_that_is_not_a_loopback_address(String uri) {
        FioClient.Builder builder = FioClient.builder(TOKEN).baseUri(URI.create(uri));

        assertThatIllegalStateException().isThrownBy(builder::build)
                .withMessage("The Fio API base URI must use https, the token travels in it; plain http is accepted only "
                        + "for 127.0.0.1 and [::1], or after allowPlainHttp(): " + uri);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://127.0.0.1/v1", "ws://fioapi.fio.cz/v1/rest", "file://fio.example/v1"})
    void should_reject_when_base_uri_set_given_scheme_that_is_neither_https_nor_http(String uri) {
        FioClient.Builder builder = FioClient.builder(TOKEN);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.baseUri(URI.create(uri)))
                .withMessageStartingWith("The Fio API base URI must use https")
                .withMessageContaining("allowPlainHttp()");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://fio_stub:8443/v1/rest", "https:///v1/rest", "http:/v1/rest", "https://fio.příklad.cz/v1"})
    void should_reject_without_sending_when_base_uri_set_given_host_the_jdk_cannot_send_to(String uri) {
        FioClient.Builder builder = FioClient.builder(TOKEN);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.baseUri(URI.create(uri)))
                .withMessageContaining("host");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fio.example/v1?x=1", "https://fio.example/v1#rest", "https://user:pw@fio.example/v1", "https://fio.example/v1?"})
    void should_reject_when_base_uri_set_given_user_info_query_or_fragment(String uri) {
        FioClient.Builder builder = FioClient.builder(TOKEN);

        assertThatIllegalArgumentException().isThrownBy(() -> builder.baseUri(URI.create(uri)))
                .withMessageContaining("user info, a query or a fragment");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://fio-stub:8089/v1/rest", "http://192.168.1.10/v1/rest", "http://localhost:8089/v1"})
    void should_send_over_plain_http_when_base_uri_set_given_explicit_opt_in(String uri) {
        RecordingHttpClient http = recording();
        FioClient pointed = FioClient.builder(TOKEN).allowPlainHttp().baseUri(URI.create(uri)).httpClient(http).build();

        pointed.bookmark().moveAfterTransaction(7);

        assertThat(http.requests()).extracting(HttpRequest::uri)
                .containsExactly(URI.create(uri + "/set-last-id/" + SECRET + "/7/"));
    }

    @Test
    void should_send_over_plain_http_when_built_given_opt_in_after_the_base_uri() {
        RecordingHttpClient http = recording();
        FioClient pointed = FioClient.builder(TOKEN).baseUri(URI.create("http://stub:8089/v1/rest")).allowPlainHttp()
                .httpClient(http).build();

        pointed.bookmark().moveAfterTransaction(7);

        assertThat(http.requests()).extracting(HttpRequest::uri)
                .containsExactly(URI.create("http://stub:8089/v1/rest/set-last-id/" + SECRET + "/7/"));
    }

    @Test
    void should_reject_when_http_client_set_given_client_that_follows_redirects() {
        FioClient.Builder builder = FioClient.builder(TOKEN);
        HttpClient following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

        assertThatIllegalArgumentException().isThrownBy(() -> builder.httpClient(following))
                .withMessageContaining("Redirect.NEVER");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8089/v1/rest", "http://[::1]:8089/v1/rest", "https://fio.example/api",
            "HTTPS://fioapi.fio.cz/v1/rest", "Https://fio.example/api", "HTTP://127.0.0.1:8089/v1/rest"})
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
    @CsvSource(delimiter = '|', value = {
            "500|the bank failed or could not read the request",
            "401|the account is not allowed",
            "403|the account is not allowed"})
    void should_raise_authorization_failure_when_downloading_given_status_the_api_uses_for_a_bad_token(int status, String reason) {
        server().respondWith(status, "<html>Internal Server Error</html>");

        FioAuthorizationException failure = catchThrowableOfType(FioAuthorizationException.class,
                () -> client().transactions().sinceBookmark().fetch());

        assertThat(failure.statusCode()).hasValue(status);
        assertThat(failure.responseBody()).hasValue("<html>Internal Server Error</html>");
        assertThat(failure).hasMessageContaining("the bank refused the token").hasMessageContaining(reason)
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void should_raise_history_locked_naming_earliest_day_when_downloading_given_unprocessable_entity() {
        server().respondHistoryLocked();

        FioHistoryLockedException failure = catchThrowableOfType(FioHistoryLockedException.class,
                () -> client().transactions().between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)).fetch());

        assertThat(failure.statusCode()).hasValue(422);
        assertThat(failure.responseBody()).hasValue(StubFioServer.historyLockedBody(LocalDate.of(2026, 7, 19)));
        assertThat(failure.earliestAvailableDay()).hasValue(LocalDate.of(2026, 7, 19));
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
    void should_raise_malformed_response_with_reason_when_downloading_given_ok_answer_that_is_not_a_statement() {
        server().respondWith(200, "<html>Údržba " + SECRET + "</html>");

        FioException failure = catchThrowableOfType(FioException.class, () -> client().transactions().sinceBookmark().fetch());

        assertThat(failure).isExactlyInstanceOf(FioMalformedResponseException.class);
        assertThat(failure.statusCode()).hasValue(200);
        assertThat(failure.responseBody()).hasValue("<html>Údržba ***</html>");
        assertThat(failure).hasMessageContaining("not valid JSON").hasMessageNotContaining(SECRET);
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_print_no_beginning_of_the_token_when_fetched_given_merchant_date_time_the_jdk_cuts_inside_the_token() {
        String merchant = Fixtures.read(Fixtures.MERCHANT_DOCUMENTATION_EXAMPLE)
                .replaceFirst("<transactionDateTime>[^<]*</transactionDateTime>",
                        "<transactionDateTime>" + "x".repeat(20) + SECRET + "</transactionDateTime>");
        server().respondWith(200, "application/xml", merchant.getBytes(StandardCharsets.UTF_8));

        FioException failure = catchThrowableOfType(FioException.class,
                () -> client().merchant().between(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16)).fetch());

        assertThat(failure).isExactlyInstanceOf(FioMalformedResponseException.class);
        assertThat(printed(failure)).contains("...").doesNotContain(SECRET.substring(0, 12));
    }

    @Test
    void should_keep_the_first_16_ki_characters_masked_when_refused_given_error_page_of_one_mebibyte() {
        server().respondWith(400, "text/html", (SECRET + "y".repeat(1024 * 1024)).getBytes(StandardCharsets.UTF_8));

        FioException failure = catchThrowableOfType(FioException.class, () -> client().transactions().sinceBookmark().fetch());

        assertThat(failure.statusCode()).hasValue(400);
        assertThat(failure.responseBody()).hasValue("***" + "y".repeat(FioException.RESPONSE_BODY_LIMIT - 3) + "\u2026");
        assertThat(failure.getMessage()).hasSizeLessThan(300);
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
                FioMalformedResponseException.class,
                FioTransportException.class,
                FioResponseTooLargeException.class);
    }
}
