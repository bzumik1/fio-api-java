package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;
import io.github.bzumik1.fio.ImportResult.Status;
import io.github.bzumik1.fio.StubFioServer.RecordedBody;
import io.github.bzumik1.fio.StubFioServer.RecordedRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.bzumik1.fio.PaymentSamples.DUE;
import static io.github.bzumik1.fio.PaymentSamples.domestic;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.PaymentSamples.foreign;
import static io.github.bzumik1.fio.PaymentSamples.fullDomestic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

class HttpPaymentsTest {

    private static final String SECRET = "notARealToken0123456789abcdefghijklmnopqrstuvwxyzABCDEF_-012345";
    private static final FioToken TOKEN = FioToken.of(SECRET);
    private static final String XML = "application/xml;charset=UTF-8";
    private static final RecordedRequest IMPORT_REQUEST = new RecordedRequest("POST", "/v1/rest/import/", "application/xml");

    private StubFioServer server;
    private FakeTime time;
    private FioClient client;

    private record Part(String name, @Nullable String fileName, @Nullable String contentType, String content) {
    }

    @BeforeEach
    void startServer() {
        server = StubFioServer.start();
        time = new FakeTime(Instant.parse("2026-10-17T08:00:00Z"));
        client = builder().build();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private FioClient.Builder builder() {
        return FioClient.builder(TOKEN).baseUri(server.baseUri()).clock(time.clock()).sleeper(time.sleeper());
    }

    private void answer(int status, String body) {
        server.respondWith(status, XML, body.getBytes(StandardCharsets.UTF_8));
    }

    private static String details(String... details) {
        return "<responseImport><result><errorCode>0</errorCode><idInstruction>9</idInstruction><status>ok</status>"
                + "<message>Dávka přijata</message></result><ordersDetails>" + String.join("", details)
                + "</ordersDetails></responseImport>";
    }

    private static String detail(int id, String text) {
        return "<detail id=\"" + id + "\"><messages><message status=\"ok\" errorCode=\"0\">" + text
                + "</message></messages></detail>";
    }

    private static List<Part> parts(RecordedBody body) {
        String boundary = body.contentType().substring(body.contentType().indexOf("boundary=") + "boundary=".length());
        String text = body.text();
        assertThat(body.contentType()).isEqualTo("multipart/form-data; boundary=" + boundary);
        assertThat(text).startsWith("--" + boundary + "\r\n").endsWith("\r\n--" + boundary + "--\r\n");
        List<Part> parts = new ArrayList<>();
        String[] chunks = text.substring(0, text.length() - ("--\r\n").length()).split(Pattern.quote("--" + boundary));
        for (int index = 1; index < chunks.length; index++) {
            String chunk = chunks[index].substring(2, chunks[index].length() - 2);
            int separator = chunk.indexOf("\r\n\r\n");
            String headers = chunk.substring(0, separator);
            parts.add(new Part(
                    header(headers, "name=\"([^\"]*)\""),
                    header(headers, "filename=\"([^\"]*)\""),
                    header(headers, "Content-Type: (.*)"),
                    chunk.substring(separator + 4)));
        }
        return parts;
    }

    private static @Nullable String header(String headers, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(headers);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String fileOf(RecordedBody body) {
        return parts(body).get(3).content();
    }

    @Test
    void should_post_fio_xml_with_type_token_and_language_parts_when_batch_submitted() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));

        ImportResult result = client.payments().batch()
                .domestic(order -> order
                        .from("1234562")
                        .to("19-2000145399/0800")
                        .amount(Money.czk("100.00"))
                        .constantSymbol("0558")
                        .variableSymbol("1234567890")
                        .specificSymbol("0000000002")
                        .date(DUE)
                        .messageForRecipient("Hračky pro děti v PENNY MARKET")
                        .comment("Dárky & <hračky>")
                        .paymentReason("110")
                        .type(DomesticPayment.Type.PRIORITY))
                .submit();

        assertThat(server.requests()).containsExactly(IMPORT_REQUEST);
        assertThat(server.bodies()).hasSize(1);
        assertThat(parts(server.bodies().get(0))).containsExactly(
                new Part("type", null, null, "xml"),
                new Part("token", null, null, SECRET),
                new Part("lng", null, null, "en"),
                new Part("file", "payments.xml", "application/octet-stream",
                        new String(new PaymentImportWriter().write(List.of(fullDomestic())), StandardCharsets.UTF_8)));
        assertThat(result).isEqualTo(new ImportResponseParser().parse(Fixtures.read(Fixtures.IMPORT_ACCEPTED)));
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.instructionId()).isEqualTo(105699806L);
    }

    @Test
    void should_write_domestic_euro_foreign_and_number_messages_by_batch_position_when_submitted_given_orders_out_of_sequence() {
        answer(200, details(detail(1, "tuzemská"), detail(2, "europlatba"), detail(3, "zahraniční")));

        ImportResult result = client.payments().batch()
                .foreign(order -> order
                        .from("1234562")
                        .to("PK36SCBL0000001123456702")
                        .bic("ALFHPKKAXXX")
                        .amount(Money.of("100", "USD"))
                        .date(DUE)
                        .beneficiary(new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK"))
                        .remittanceInfo("Payment for hotel 032013")
                        .paymentReason("348"))
                .add(domestic())
                .euro(order -> order
                        .from("1234562")
                        .to("AT611904300234573201")
                        .amount(PaymentSamples.euro("100.00"))
                        .date(DUE)
                        .beneficiary("Hans Gruber"))
                .submit();

        assertThat(fileOf(server.bodies().get(0))).isEqualTo(
                new String(new PaymentImportWriter().write(List.of(domestic(), euro(), foreign())), StandardCharsets.UTF_8));
        assertThat(result.messages()).containsExactly(
                new Message(null, null, "Dávka přijata"),
                new Message(2, Status.OK, "tuzemská"),
                new Message(3, Status.OK, "europlatba"),
                new Message(1, Status.OK, "zahraniční"));
        assertThat(result.instructionId()).isEqualTo(9L);
    }

    @Test
    void should_leave_every_order_number_empty_when_submitted_given_detail_id_outside_the_batch() {
        answer(200, details(detail(1, "tuzemská"), detail(0, "bez pořadí"), detail(3, "mimo dávku")));

        ImportResult result = client.payments().batch().add(domestic()).add(euro()).submit();

        assertThat(result.messages()).extracting(Message::orderNumber).containsOnlyNulls();
        assertThat(result.messages()).extracting(Message::text)
                .containsExactly("Dávka přijata", "tuzemská", "bez pořadí", "mimo dávku");
    }

    @Test
    void should_submit_ready_batch_when_submitted_given_payment_batch() {
        answer(200, details(detail(1, "první"), detail(2, "druhá")));

        ImportResult result = client.payments().submit(PaymentBatch.of(euro(), domestic()));

        assertThat(fileOf(server.bodies().get(0))).containsSubsequence("<DomesticTransaction>", "<T2Transaction>");
        assertThat(result.messages()).extracting(Message::orderNumber).containsExactly(null, 2, 1);
    }

    @Test
    void should_ask_for_messages_in_chosen_language_when_submitted_given_message_locale() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));

        builder().messageLocale(Locale.forLanguageTag("cs-CZ")).build().payments().submit(PaymentBatch.of(domestic()));

        assertThat(parts(server.bodies().get(0))).extracting(Part::name, Part::content)
                .contains(tuple("lng", "cs"));
    }

    @Test
    void should_upload_file_unchanged_with_its_type_and_keep_file_positions_when_uploaded() {
        answer(200, details(detail(1, "první"), detail(7, "sedmá")));
        byte[] abo = "UHL1201012Novak Jan           1234567890001999\r\n".getBytes(StandardCharsets.US_ASCII);

        ImportResult result = client.payments().upload(new PaymentFile(PaymentFileType.ABO, abo));

        assertThat(server.requests()).containsExactly(IMPORT_REQUEST);
        assertThat(parts(server.bodies().get(0))).containsExactly(
                new Part("type", null, null, "abo"),
                new Part("token", null, null, SECRET),
                new Part("lng", null, null, "en"),
                new Part("file", "payments.abo", "application/octet-stream", new String(abo, StandardCharsets.US_ASCII)));
        assertThat(result.messages()).extracting(Message::orderNumber).containsExactly(null, 1, 7);
    }

    @Test
    void should_name_pain_file_as_xml_when_uploaded_given_iso_20022_file() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));

        client.payments().upload(new PaymentFile(PaymentFileType.PAIN_001, "<Document/>".getBytes(StandardCharsets.UTF_8)));

        assertThat(parts(server.bodies().get(0))).extracting(Part::name, Part::fileName, Part::content).containsExactly(
                tuple("type", null, "pain001_xml"),
                tuple("token", null, SECRET),
                tuple("lng", null, "en"),
                tuple("file", "payments.xml", "<Document/>"));
    }

    @Test
    void should_return_refused_result_without_failing_when_submitted_given_ok_answer_with_error_status() {
        answer(200, Fixtures.read(Fixtures.IMPORT_REFUSED));

        ImportResult result = client.payments().submit(PaymentBatch.of(domestic(), domestic()));

        assertThat(result.status()).isEqualTo(Status.ERROR);
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo(1);
        assertThat(result.messages()).contains(new Message(2, Status.ERROR, "Neplatný kód banky příjemce."));
    }

    @Test
    void should_reject_without_request_when_submitted_given_empty_batch() {
        FioClient.BatchRequest batch = client.payments().batch();

        assertThatIllegalStateException().isThrownBy(batch::submit)
                .withMessage("The batch holds no payment order to submit");
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_reject_without_request_when_order_added_given_incomplete_or_missing_order() {
        FioClient.BatchRequest batch = client.payments().batch();

        assertThatIllegalStateException().isThrownBy(() -> batch.domestic(order -> order.from("1234562")))
                .withMessage("The recipient's account is required");
        assertThatIllegalStateException().isThrownBy(() -> batch.euro(order -> order.from("1234562")))
                .withMessage("The recipient's account is required");
        assertThatIllegalStateException().isThrownBy(() -> batch.foreign(order -> order.from("1234562")))
                .withMessage("The recipient's account is required");
        assertThatNullPointerException().isThrownBy(() -> batch.domestic(null)).withMessage("order");
        assertThatNullPointerException().isThrownBy(() -> batch.add(null)).withMessage("order");
        assertThatNullPointerException().isThrownBy(() -> client.payments().submit(null)).withMessage("batch");
        assertThatNullPointerException().isThrownBy(() -> client.payments().upload(null)).withMessage("file");
        assertThatIllegalStateException().isThrownBy(batch::submit);
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_start_empty_batch_each_time_when_batch_started() {
        FioClient.BatchRequest first = client.payments().batch().add(domestic());

        assertThat(client.payments().batch()).isNotSameAs(first);
        assertThatIllegalStateException().isThrownBy(() -> client.payments().batch().submit());
    }

    @Test
    void should_reject_without_request_when_submitted_given_orders_larger_than_fio_imports() {
        List<PaymentOrder> orders = new ArrayList<>();
        for (int order = 0; order < 5000; order++) {
            orders.add(fullDomestic());
        }

        assertThatIllegalArgumentException().isThrownBy(() -> client.payments().submit(new PaymentBatch(orders)))
                .withMessageStartingWith("A payment file is at most 2097152 bytes");
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_raise_failure_with_status_and_masked_body_when_submitted_given_http_error_with_import_answer() {
        answer(400, "<responseImport><result><errorCode>11</errorCode><status>error</status>"
                + "<message>Syntaktická chyba, token " + SECRET + "</message></result></responseImport>");

        FioException failure = catchThrowableOfType(FioException.class,
                () -> client.payments().submit(PaymentBatch.of(domestic())));

        assertThat(failure).isExactlyInstanceOf(FioException.class)
                .hasMessage("Fio API request POST " + server.baseUri() + "/import/ was refused with HTTP 400");
        assertThat(failure.statusCode()).hasValue(400);
        assertThat(failure.responseBody()).hasValueSatisfying(body -> assertThat(body)
                .contains("<errorCode>11</errorCode>", "token ***")
                .doesNotContain(SECRET));
    }

    @Test
    void should_raise_authorization_failure_when_submitted_given_inactive_token() {
        answer(500, "");

        assertThat(catchThrowableOfType(FioAuthorizationException.class,
                () -> client.payments().submit(PaymentBatch.of(domestic())))).isNotNull();
    }

    @Test
    void should_raise_failure_without_token_when_submitted_given_ok_answer_this_client_does_not_understand() {
        answer(200, "<" + SECRET + ">Chyba</" + SECRET + ">");

        FioException failure = catchThrowableOfType(FioException.class,
                () -> client.payments().submit(PaymentBatch.of(domestic())));

        assertThat(failure).hasMessageContaining("does not understand: The body is a <***>, not a <responseImport>");
        assertThat(failure.responseBody()).hasValue("<***>Chyba</***>");
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        assertThat(trace.toString()).doesNotContain(SECRET);
    }

    @Test
    void should_send_same_file_again_after_thirty_seconds_when_bank_refuses_given_rate_limit() {
        server.enqueue(409, XML, new byte[0]);
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));

        ImportResult result = client.payments().submit(PaymentBatch.of(domestic()));

        assertThat(result.isAccepted()).isTrue();
        assertThat(server.requests()).containsExactly(IMPORT_REQUEST, IMPORT_REQUEST);
        assertThat(fileOf(server.bodies().get(1))).isEqualTo(fileOf(server.bodies().get(0)));
        assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(30));
    }
}
