package io.github.bzumik1.fio;

import io.github.bzumik1.fio.StubFioServer.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static io.github.bzumik1.fio.Fixtures.MERCHANT_DOCUMENTATION_EXAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class HttpMerchantTest {

    private static final String SECRET = "aGEMtmwcsWAjPzhg3bPH3j7Iu15g56d66AdEbfIjqIgMR9Idh6u3";
    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");
    private static final String XML = "application/xml";
    private static final LocalDate FROM = LocalDate.of(2012, 7, 1);
    private static final LocalDate TO = LocalDate.of(2012, 7, 31);

    private StubFioServer server;
    private FioClient client;

    @BeforeEach
    void startServer() {
        server = StubFioServer.start();
        FakeTime time = new FakeTime(NOW);
        client = FioClient.builder(FioToken.of(SECRET))
                .baseUri(server.baseUri())
                .clock(time.clock())
                .sleeper(time.sleeper())
                .build();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void should_download_xml_and_parse_it_when_period_fetched_given_documentation_example() {
        server.respondWith(200, "application/xml;charset=UTF-8",
                Fixtures.read(MERCHANT_DOCUMENTATION_EXAMPLE).getBytes(StandardCharsets.UTF_8));

        MerchantStatement statement = client.merchant().between(FROM, TO).fetch();

        assertThat(server.requests()).containsExactly(
                new RecordedRequest("GET", "/v1/rest/merchant/" + SECRET + "/2012-07-01/2012-07-31/transactions.xml", XML));
        assertThat(statement.account()).isEqualTo(DomesticAccount.parse("2111111111/2010"));
        assertThat(statement.transactions()).extracting(CardTransaction::operationId).containsExactly(9277937165L);
    }

    @Test
    void should_download_single_day_when_period_fetched_given_same_start_and_end() {
        server.respondWith(200, XML, Fixtures.read(MERCHANT_DOCUMENTATION_EXAMPLE).getBytes(StandardCharsets.UTF_8));

        client.merchant().between(TO, TO).fetch();

        assertThat(server.requests()).extracting(RecordedRequest::path)
                .containsExactly("/v1/rest/merchant/" + SECRET + "/2012-07-31/2012-07-31/transactions.xml");
    }

    @ParameterizedTest
    @EnumSource(MerchantFormat.class)
    void should_download_raw_format_with_its_extension_when_period_downloaded_given_format(MerchantFormat format) {
        byte[] body = "accountId;2111111111".getBytes(StandardCharsets.UTF_8);
        server.respondWith(200, format.mediaType() + ";charset=UTF-8", body);

        Download document = client.merchant().between(FROM, TO).download(format);

        assertThat(server.requests()).containsExactly(new RecordedRequest(
                "GET",
                "/v1/rest/merchant/" + SECRET + "/2012-07-01/2012-07-31/transactions." + format.extension(),
                format.mediaType()));
        assertThat(document).isEqualTo(new Download(format.mediaType() + ";charset=UTF-8", StandardCharsets.UTF_8, body));
    }

    @Test
    void should_reject_without_request_when_period_chosen_given_end_before_start() {
        assertThatIllegalArgumentException().isThrownBy(() -> client.merchant().between(TO, FROM));
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_reject_without_request_when_period_chosen_given_no_start() {
        assertThatNullPointerException().isThrownBy(() -> client.merchant().between(null, TO));
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_reject_without_request_when_period_chosen_given_no_end() {
        assertThatNullPointerException().isThrownBy(() -> client.merchant().between(FROM, null));
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_read_czech_text_when_fetched_given_windows_1250_statement_with_matching_declaration() {
        String declared = "<?xml version=\"1.0\" encoding=\"windows-1250\"?>\n" + Fixtures.read(MERCHANT_DOCUMENTATION_EXAMPLE);
        server.respondWith(200, "application/xml;charset=windows-1250", declared.getBytes(Charset.forName("windows-1250")));

        MerchantStatement statement = client.merchant().between(FROM, TO).fetch();

        assertThat(statement.transactions()).extracting(CardTransaction::note)
                .containsExactly("Zaúčtování POS terminálů MasterCard, Operace ON-US");
    }

    @Test
    void should_decode_as_the_body_declares_when_fetched_given_content_type_naming_another_charset() {
        server.respondWith(200, "application/xml;charset=windows-1250",
                Fixtures.read(MERCHANT_DOCUMENTATION_EXAMPLE).getBytes(StandardCharsets.UTF_8));

        MerchantStatement statement = client.merchant().between(FROM, TO).fetch();

        assertThat(statement.transactions()).extracting(CardTransaction::note)
                .containsExactly("Zaúčtování POS terminálů MasterCard, Operace ON-US");
    }

    @Test
    void should_reject_without_request_when_downloaded_given_no_format() {
        FioClient.MerchantRequest request = client.merchant().between(FROM, TO);

        assertThatNullPointerException().isThrownBy(() -> request.download(null)).withMessage("format");
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_raise_generic_failure_without_token_when_fetched_given_ok_answer_that_is_not_a_merchant_statement() {
        server.respondWith(200, "text/html", ("<html><body>Údržba " + SECRET + "</body></html>").getBytes(StandardCharsets.UTF_8));

        FioException failure = catchThrowableOfType(FioException.class, () -> client.merchant().between(FROM, TO).fetch());

        assertThat(failure.getClass()).isEqualTo(FioException.class);
        assertThat(failure.statusCode()).hasValue(200);
        assertThat(failure).hasMessageContaining("The root element is html, not MerchantStatement")
                .hasMessageNotContaining(SECRET);
        assertThat(failure.responseBody()).hasValue("<html><body>Údržba ***</body></html>");
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_raise_authorization_failure_when_fetched_given_bank_refusing_the_token() {
        server.respondWith(500, "");

        FioException failure = catchThrowableOfType(FioException.class, () -> client.merchant().between(FROM, TO).fetch());

        assertThat(failure).isExactlyInstanceOf(FioAuthorizationException.class)
                .hasMessage("Fio API request GET " + server.baseUri() + "/merchant/***/2012-07-01/2012-07-31/transactions.xml"
                        + " was refused with HTTP 500: the token does not exist, has expired or is not active");
        assertThat(failure.statusCode()).hasValue(500);
    }

    private static String printed(Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        return trace.toString();
    }
}
