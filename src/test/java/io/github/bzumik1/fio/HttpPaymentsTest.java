package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;
import io.github.bzumik1.fio.ImportResult.Status;
import io.github.bzumik1.fio.ImportResult.Sum;
import io.github.bzumik1.fio.StubFioServer.RecordedBody;
import io.github.bzumik1.fio.StubFioServer.RecordedRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.PaymentSamples.DUE;
import static io.github.bzumik1.fio.PaymentSamples.domestic;
import static io.github.bzumik1.fio.PaymentSamples.euro;
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
    private static final String FILE_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Import xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:noNamespaceSchemaLocation=\"http://www.fio.cz/schema/importIB.xsd\"><Orders>";
    private static final String FILE_END = "</Orders></Import>";
    private static final String MINIMAL_DOMESTIC = "<DomesticTransaction><accountFrom>1234562</accountFrom><currency>CZK</currency>"
            + "<amount>100.00</amount><accountTo>19-2000145399</accountTo><bankCode>0800</bankCode><date>2026-10-20</date>"
            + "<paymentType>431001</paymentType></DomesticTransaction>";
    private static final String MINIMAL_EURO = "<T2Transaction><accountFrom>1234562</accountFrom><currency>EUR</currency>"
            + "<amount>100.00</amount><accountTo>AT611904300234573201</accountTo><date>2026-10-20</date>"
            + "<benefName>Hans Gruber</benefName><paymentType>431008</paymentType></T2Transaction>";
    private static final String MINIMAL_FOREIGN = "<ForeignTransaction><accountFrom>1234562</accountFrom><currency>USD</currency>"
            + "<amount>100.00</amount><accountTo>PK36SCBL0000001123456702</accountTo><bic>ALFHPKKAXXX</bic>"
            + "<date>2026-10-20</date><benefName>Amir Khan</benefName><benefStreet>Nishtar Rd 13</benefStreet>"
            + "<benefCity>Karachi</benefCity><benefCountry>PK</benefCountry>"
            + "<remittanceInfo1>Payment for hotel 032013</remittanceInfo1><detailsOfCharges>470503</detailsOfCharges>"
            + "<paymentReason>348</paymentReason></ForeignTransaction>";
    private static final ImportResult ACCEPTED = new ImportResult(0, Status.OK, 105699806L,
            List.of(new Sum("CZK", new BigDecimal("100.00"), new BigDecimal("0"))), List.of(new Message(1, Status.OK, "OK")));

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
    void should_post_fio_xml_with_type_token_and_language_parts_when_batch_submitted_given_full_domestic_order() {
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
                new Part("file", "payments.xml", "application/octet-stream", FILE_START
                        + "<DomesticTransaction><accountFrom>1234562</accountFrom><currency>CZK</currency><amount>100.00</amount>"
                        + "<accountTo>19-2000145399</accountTo><bankCode>0800</bankCode><ks>0558</ks><vs>1234567890</vs>"
                        + "<ss>0000000002</ss><date>2026-10-20</date>"
                        + "<messageForRecipient>Hračky pro děti v PENNY MARKET</messageForRecipient>"
                        + "<comment>Dárky &amp; &lt;hračky&gt;</comment><paymentReason>110</paymentReason>"
                        + "<paymentType>431005</paymentType></DomesticTransaction>" + FILE_END));
        assertThat(result).isEqualTo(ACCEPTED);
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

        assertThat(fileOf(server.bodies().get(0))).isEqualTo(FILE_START + MINIMAL_DOMESTIC + MINIMAL_EURO + MINIMAL_FOREIGN + FILE_END);
        assertThat(result).isEqualTo(new ImportResult(0, Status.OK, 9L, List.of(), List.of(
                new Message(null, null, "Dávka přijata"),
                new Message(2, Status.OK, "tuzemská"),
                new Message(3, Status.OK, "europlatba"),
                new Message(1, Status.OK, "zahraniční"))));
    }

    @Test
    void should_number_the_last_order_when_submitted_given_detail_id_equal_to_the_batch_size() {
        answer(200, details(detail(2, "druhá v souboru")));

        ImportResult result = client.payments().batch().add(euro()).add(domestic()).submit();

        assertThat(result.messages()).containsExactly(
                new Message(null, null, "Dávka přijata"), new Message(1, Status.OK, "druhá v souboru"));
    }

    @Test
    void should_leave_every_order_number_empty_when_submitted_given_detail_id_beyond_the_batch_size() {
        answer(200, details(detail(1, "první"), detail(3, "mimo dávku")));

        ImportResult result = client.payments().batch().add(domestic()).add(euro()).submit();

        assertThat(result.messages()).containsExactly(
                new Message(null, null, "Dávka přijata"),
                new Message(null, Status.OK, "první"),
                new Message(null, Status.OK, "mimo dávku"));
    }

    @Test
    void should_leave_every_order_number_empty_when_submitted_given_negative_detail_id() {
        answer(200, details(detail(-1, "záporná"), detail(1, "první")));

        ImportResult result = client.payments().batch().add(domestic()).submit();

        assertThat(result.messages()).extracting(Message::orderNumber).containsOnlyNulls();
    }

    @Test
    void should_keep_detail_without_id_unnumbered_and_number_the_others_when_submitted_given_mixed_details() {
        answer(200, details("<detail><messages><message status=\"warning\">bez čísla</message></messages></detail>",
                detail(1, "první")));

        ImportResult result = client.payments().batch().add(euro()).add(domestic()).submit();

        assertThat(result.messages()).containsExactly(
                new Message(null, null, "Dávka přijata"),
                new Message(null, Status.WARNING, "bez čísla"),
                new Message(2, Status.OK, "první"));
    }

    @Test
    void should_read_czech_messages_when_submitted_given_answer_served_in_windows_1250() {
        String refused = Fixtures.read(Fixtures.IMPORT_REFUSED).replace("encoding=\"UTF-8\"", "encoding=\"windows-1250\"");
        server.respondWith(200, "application/xml;charset=windows-1250", refused.getBytes(Charset.forName("windows-1250")));

        ImportResult result = client.payments().submit(PaymentBatch.of(domestic(), domestic()));

        assertThat(result.messages()).extracting(Message::text).containsExactly(
                "Nalezené chyby při kontrole příkazů", "OK", "Neplatný kód banky příjemce.", "Měna platby neodpovídá měně účtu.");
    }

    @Test
    void should_refuse_a_second_submission_without_request_when_submitted_twice_given_submitted_batch() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));
        FioClient.BatchRequest batch = client.payments().batch().add(domestic());
        batch.submit();

        assertThatIllegalStateException().isThrownBy(batch::submit)
                .withMessage("The batch was already submitted; build a new one to submit again");
        assertThat(server.requests()).containsExactly(IMPORT_REQUEST);
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

        assertThat(fileOf(server.bodies().get(0))).isEqualTo(FILE_START + MINIMAL_DOMESTIC + MINIMAL_EURO + FILE_END);
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
    void should_upload_file_unchanged_with_its_type_and_keep_file_positions_when_uploaded_given_abo_file() {
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

        assertThat(result).isEqualTo(new ImportResult(1, Status.ERROR, null,
                List.of(new Sum("CZK", new BigDecimal("350.50"), new BigDecimal("0")),
                        new Sum("EUR", new BigDecimal("12.00"), new BigDecimal("0"))),
                List.of(new Message(null, null, "Nalezené chyby při kontrole příkazů"),
                        new Message(1, Status.OK, "OK"),
                        new Message(2, Status.ERROR, "Neplatný kód banky příjemce."),
                        new Message(2, Status.WARNING, "Měna platby neodpovídá měně účtu."))));
    }

    @Test
    void should_reject_without_request_when_submitted_given_empty_batch() {
        FioClient.BatchRequest batch = client.payments().batch();

        assertThatIllegalStateException().isThrownBy(batch::submit)
                .withMessage("The batch holds no payment order to submit");
        assertThat(server.requests()).isEmpty();
    }

    static Stream<Arguments> incompleteOrders() {
        return Stream.of(
                Arguments.of("domestic", (Consumer<FioClient.BatchRequest>) batch -> batch.domestic(order -> order.from("1234562"))),
                Arguments.of("euro", (Consumer<FioClient.BatchRequest>) batch -> batch.euro(order -> order.from("1234562"))),
                Arguments.of("foreign", (Consumer<FioClient.BatchRequest>) batch -> batch.foreign(order -> order.from("1234562"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteOrders")
    void should_reject_without_request_when_order_added_given_order_without_recipient(
            String kind, Consumer<FioClient.BatchRequest> addition) {
        FioClient.BatchRequest batch = client.payments().batch();

        assertThatIllegalStateException().isThrownBy(() -> addition.accept(batch))
                .withMessage("The recipient's account is required");
        assertThatIllegalStateException().isThrownBy(batch::submit).withMessage("The batch holds no payment order to submit");
        assertThat(server.requests()).isEmpty();
    }

    static Stream<Arguments> missingArguments() {
        return Stream.of(
                Arguments.of("order", (Consumer<FioClient>) fio -> fio.payments().batch().domestic(null)),
                Arguments.of("order", (Consumer<FioClient>) fio -> fio.payments().batch().euro(null)),
                Arguments.of("order", (Consumer<FioClient>) fio -> fio.payments().batch().foreign(null)),
                Arguments.of("order", (Consumer<FioClient>) fio -> fio.payments().batch().add(null)),
                Arguments.of("batch", (Consumer<FioClient>) fio -> fio.payments().submit(null)),
                Arguments.of("file", (Consumer<FioClient>) fio -> fio.payments().upload(null)));
    }

    @ParameterizedTest
    @MethodSource("missingArguments")
    void should_reject_naming_the_argument_without_request_when_called_given_null(String argument, Consumer<FioClient> call) {
        assertThatNullPointerException().isThrownBy(() -> call.accept(client)).withMessage(argument);
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_start_an_empty_batch_when_batch_started_given_orders_added_to_another_batch() {
        client.payments().batch().add(domestic());

        assertThatIllegalStateException().isThrownBy(() -> client.payments().batch().submit())
                .withMessage("The batch holds no payment order to submit");
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

        FioAuthorizationException failure = catchThrowableOfType(FioAuthorizationException.class,
                () -> client.payments().submit(PaymentBatch.of(domestic())));

        assertThat(failure).hasMessageContaining("POST " + server.baseUri() + "/import/")
                .hasMessageContaining("refused with HTTP 500")
                .hasMessageContaining("could not read the request");
        assertThat(failure.statusCode()).hasValue(500);
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

        assertThat(result).isEqualTo(ACCEPTED);
        assertThat(server.requests()).containsExactly(IMPORT_REQUEST, IMPORT_REQUEST);
        assertThat(fileOf(server.bodies().get(1))).isEqualTo(fileOf(server.bodies().get(0)));
        assertThat(time.sleeps()).containsExactly(Duration.ofSeconds(30));
    }
}
