package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;
import io.github.bzumik1.fio.ImportResult.Status;
import io.github.bzumik1.fio.ImportResult.Sum;
import io.github.bzumik1.fio.StubFioServer.RecordedBody;
import io.github.bzumik1.fio.StubFioServer.RecordedRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.PaymentSamples.DUE;
import static io.github.bzumik1.fio.PaymentSamples.domestic;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.StubBank.NOW;
import static io.github.bzumik1.fio.StubBank.SECRET;
import static io.github.bzumik1.fio.Throwables.printed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class HttpPaymentsTest {

    private static final String MULTIPART = "multipart/form-data; boundary=";
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

    @RegisterExtension
    final StubBank bank = new StubBank();

    private record Part(String name, @Nullable String fileName, @Nullable String contentType, String content) {
    }

    private record Section(String headers, byte[] content) {
    }

    private StubFioServer server() {
        return bank.server();
    }

    private FioClient client() {
        return bank.client();
    }

    private void answer(int status, String body) {
        server().respondWith(status, XML, body.getBytes(StandardCharsets.UTF_8));
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

    private static String boundaryOf(RecordedBody body) {
        assertThat(body.contentType()).startsWith(MULTIPART);
        String boundary = body.contentType().substring(MULTIPART.length());
        assertThat(boundary).isNotEmpty();
        return boundary;
    }

    private static List<Section> sections(RecordedBody body) {
        byte[] content = body.content();
        String boundary = boundaryOf(body);
        byte[] opening = ascii("--" + boundary + "\r\n");
        byte[] delimiter = ascii("\r\n--" + boundary);
        byte[] closing = ascii("\r\n--" + boundary + "--\r\n");
        assertThat(Arrays.copyOfRange(content, 0, opening.length)).isEqualTo(opening);
        assertThat(Arrays.copyOfRange(content, content.length - closing.length, content.length)).isEqualTo(closing);
        List<Section> sections = new ArrayList<>();
        int start = opening.length;
        while (true) {
            int end = indexOf(content, delimiter, start);
            int separator = indexOf(content, ascii("\r\n\r\n"), start);
            assertThat(separator).isBetween(start, end);
            sections.add(new Section(
                    new String(content, start, separator - start, StandardCharsets.UTF_8),
                    Arrays.copyOfRange(content, separator + 4, end)));
            if (end == content.length - closing.length) {
                return sections;
            }
            start = end + delimiter.length;
            assertThat(Arrays.copyOfRange(content, start, start + 2)).isEqualTo(ascii("\r\n"));
            start += 2;
        }
    }

    private static List<Part> parts(RecordedBody body) {
        List<Part> parts = new ArrayList<>();
        for (Section section : sections(body)) {
            parts.add(new Part(
                    header(section.headers(), "name=\"([^\"]*)\""),
                    header(section.headers(), "filename=\"([^\"]*)\""),
                    header(section.headers(), "Content-Type: (.*)"),
                    new String(section.content(), StandardCharsets.UTF_8)));
        }
        return parts;
    }

    private static byte[] fileBytesOf(RecordedBody body) {
        return sections(body).get(3).content();
    }

    private static int indexOf(byte[] data, byte[] target, int from) {
        for (int index = from; index <= data.length - target.length; index++) {
            if (Arrays.equals(data, index, index + target.length, target, 0, target.length)) {
                return index;
            }
        }
        return -1;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
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

        ImportResult result = client().payments().batch()
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

        assertThat(server().requests()).containsExactly(IMPORT_REQUEST);
        assertThat(server().bodies()).hasSize(1);
        assertThat(parts(server().bodies().get(0))).containsExactly(
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

        ImportResult result = client().payments().batch()
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

        assertThat(fileOf(server().bodies().get(0))).isEqualTo(FILE_START + MINIMAL_DOMESTIC + MINIMAL_EURO + MINIMAL_FOREIGN + FILE_END);
        assertThat(result).isEqualTo(new ImportResult(0, Status.OK, 9L, List.of(), List.of(
                new Message(null, null, "Dávka přijata"),
                new Message(2, Status.OK, "tuzemská"),
                new Message(3, Status.OK, "europlatba"),
                new Message(1, Status.OK, "zahraniční"))));
    }

    @Test
    void should_number_the_last_order_when_submitted_given_detail_id_equal_to_the_batch_size() {
        answer(200, details(detail(2, "druhá v souboru")));

        ImportResult result = client().payments().batch().add(euro()).add(domestic()).submit();

        assertThat(result.messages()).containsExactly(
                new Message(null, null, "Dávka přijata"), new Message(1, Status.OK, "druhá v souboru"));
    }

    @Test
    void should_leave_every_order_number_empty_when_submitted_given_detail_id_beyond_the_batch_size() {
        answer(200, details(detail(1, "první"), detail(3, "mimo dávku")));

        ImportResult result = client().payments().batch().add(domestic()).add(euro()).submit();

        assertThat(result.messages()).containsExactly(
                new Message(null, null, "Dávka přijata"),
                new Message(null, Status.OK, "první"),
                new Message(null, Status.OK, "mimo dávku"));
    }

    @Test
    void should_leave_every_order_number_empty_when_submitted_given_negative_detail_id() {
        answer(200, details(detail(-1, "záporná"), detail(1, "první")));

        ImportResult result = client().payments().batch().add(domestic()).submit();

        assertThat(result.messages()).extracting(Message::orderNumber).containsOnlyNulls();
    }

    @Test
    void should_keep_detail_without_id_unnumbered_and_number_the_others_when_submitted_given_mixed_details() {
        answer(200, details("<detail><messages><message status=\"warning\">bez čísla</message></messages></detail>",
                detail(1, "první")));

        ImportResult result = client().payments().batch().add(euro()).add(domestic()).submit();

        assertThat(result.messages()).containsExactly(
                new Message(null, null, "Dávka přijata"),
                new Message(null, Status.WARNING, "bez čísla"),
                new Message(2, Status.OK, "první"));
    }

    @Test
    void should_read_czech_messages_when_submitted_given_answer_served_in_windows_1250() {
        String refused = Fixtures.read(Fixtures.IMPORT_REFUSED).replace("encoding=\"UTF-8\"", "encoding=\"windows-1250\"");
        server().respondWith(200, "application/xml;charset=windows-1250", refused.getBytes(Charset.forName("windows-1250")));

        ImportResult result = client().payments().submit(PaymentBatch.of(domestic(), domestic()));

        assertThat(result.messages()).extracting(Message::text).containsExactly(
                "Nalezené chyby při kontrole příkazů", "OK", "Neplatný kód banky příjemce.", "Měna platby neodpovídá měně účtu.");
    }

    @Test
    void should_refuse_a_second_submission_without_request_when_submitted_twice_given_submitted_batch() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));
        FioClient.BatchRequest batch = client().payments().batch().add(domestic());
        batch.submit();

        assertThatIllegalStateException().isThrownBy(batch::submit)
                .withMessage("The batch was already submitted; build a new one to submit again");
        assertThat(server().requests()).containsExactly(IMPORT_REQUEST);
    }

    @Test
    void should_leave_every_order_number_empty_when_submitted_given_detail_id_outside_the_batch() {
        answer(200, details(detail(1, "tuzemská"), detail(0, "bez pořadí"), detail(3, "mimo dávku")));

        ImportResult result = client().payments().batch().add(domestic()).add(euro()).submit();

        assertThat(result.messages()).extracting(Message::orderNumber).containsOnlyNulls();
        assertThat(result.messages()).extracting(Message::text)
                .containsExactly("Dávka přijata", "tuzemská", "bez pořadí", "mimo dávku");
    }

    @Test
    void should_submit_ready_batch_when_submitted_given_payment_batch() {
        answer(200, details(detail(1, "první"), detail(2, "druhá")));

        ImportResult result = client().payments().submit(PaymentBatch.of(euro(), domestic()));

        assertThat(fileOf(server().bodies().get(0))).isEqualTo(FILE_START + MINIMAL_DOMESTIC + MINIMAL_EURO + FILE_END);
        assertThat(result.messages()).extracting(Message::orderNumber).containsExactly(null, 2, 1);
    }

    @Test
    void should_ask_for_messages_in_chosen_language_when_submitted_given_message_locale() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));

        bank.builder().messageLocale(Locale.forLanguageTag("cs-CZ")).build().payments().submit(PaymentBatch.of(domestic()));

        assertThat(parts(server().bodies().get(0))).extracting(Part::name, Part::content)
                .contains(tuple("lng", "cs"));
    }

    @Test
    void should_upload_file_unchanged_with_its_type_and_keep_file_positions_when_uploaded_given_abo_file() {
        answer(200, details(detail(1, "první"), detail(7, "sedmá")));
        byte[] abo = "UHL1201012Novak Jan           1234567890001999\r\n".getBytes(StandardCharsets.US_ASCII);

        ImportResult result = client().payments().upload(new PaymentFile(PaymentFileType.ABO, abo));

        assertThat(server().requests()).containsExactly(IMPORT_REQUEST);
        assertThat(parts(server().bodies().get(0))).containsExactly(
                new Part("type", null, null, "abo"),
                new Part("token", null, null, SECRET),
                new Part("lng", null, null, "en"),
                new Part("file", "payments.abo", "application/octet-stream", new String(abo, StandardCharsets.US_ASCII)));
        assertThat(result.messages()).extracting(Message::orderNumber).containsExactly(null, 1, 7);
    }

    @Test
    void should_upload_every_byte_value_unchanged_when_uploaded_given_binary_file() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));
        byte[] file = new byte[512];
        for (int index = 0; index < file.length; index++) {
            file[index] = (byte) index;
        }

        client().payments().upload(new PaymentFile(PaymentFileType.ABO, file));

        RecordedBody body = server().bodies().get(0);
        assertThat(new String(file, StandardCharsets.ISO_8859_1)).doesNotContain(boundaryOf(body));
        assertThat(fileBytesOf(body)).isEqualTo(file);
    }

    @Test
    void should_upload_windows_1250_bytes_unchanged_when_uploaded_given_abo_line_with_czech_name() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));
        byte[] abo = "UHL1201012Nováček Jan         1234567890001999\r\n".getBytes(Charset.forName("windows-1250"));

        client().payments().upload(new PaymentFile(PaymentFileType.ABO, abo));

        assertThat(new String(abo, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8)).isNotEqualTo(abo);
        assertThat(fileBytesOf(server().bodies().get(0))).isEqualTo(abo);
    }

    @Test
    void should_name_pain_file_as_xml_when_uploaded_given_iso_20022_file() {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));

        client().payments().upload(new PaymentFile(PaymentFileType.PAIN_001, "<Document/>".getBytes(StandardCharsets.UTF_8)));

        assertThat(parts(server().bodies().get(0))).extracting(Part::name, Part::fileName, Part::content).containsExactly(
                tuple("type", null, "pain001_xml"),
                tuple("token", null, SECRET),
                tuple("lng", null, "en"),
                tuple("file", "payments.xml", "<Document/>"));
    }

    @Test
    void should_return_refused_result_without_failing_when_submitted_given_ok_answer_with_error_status() {
        answer(200, Fixtures.read(Fixtures.IMPORT_REFUSED));

        ImportResult result = client().payments().submit(PaymentBatch.of(domestic(), domestic()));

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
        FioClient.BatchRequest batch = client().payments().batch();

        assertThatIllegalStateException().isThrownBy(batch::submit)
                .withMessage("The batch holds no payment order to submit");
        assertThat(server().requests()).isEmpty();
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
        FioClient.BatchRequest batch = client().payments().batch();

        assertThatIllegalStateException().isThrownBy(() -> addition.accept(batch))
                .withMessage("The recipient's account is required");
        assertThatIllegalStateException().isThrownBy(batch::submit).withMessage("The batch holds no payment order to submit");
        assertThat(server().requests()).isEmpty();
    }

    static Stream<Arguments> missingArguments() {
        return Stream.of(
                argumentSet("domestic order", "order", (Consumer<FioClient>) fio -> fio.payments().batch().domestic(null)),
                argumentSet("euro order", "order", (Consumer<FioClient>) fio -> fio.payments().batch().euro(null)),
                argumentSet("foreign order", "order", (Consumer<FioClient>) fio -> fio.payments().batch().foreign(null)),
                argumentSet("ready order", "order", (Consumer<FioClient>) fio -> fio.payments().batch().add(null)),
                argumentSet("batch", "batch", (Consumer<FioClient>) fio -> fio.payments().submit(null)),
                argumentSet("file", "file", (Consumer<FioClient>) fio -> fio.payments().upload(null)));
    }

    @ParameterizedTest
    @MethodSource("missingArguments")
    void should_reject_naming_the_argument_without_request_when_called_given_null(String argument, Consumer<FioClient> call) {
        assertThatNullPointerException().isThrownBy(() -> call.accept(client())).withMessage(argument);
        assertThat(server().requests()).isEmpty();
    }

    @Test
    void should_start_an_empty_batch_when_batch_started_given_orders_added_to_another_batch() {
        client().payments().batch().add(domestic());

        assertThatIllegalStateException().isThrownBy(() -> client().payments().batch().submit())
                .withMessage("The batch holds no payment order to submit");
    }

    private static List<PaymentOrder> ordersLargerThanFioImports() {
        DomesticPayment longest = PaymentSamples.domesticBuilder()
                .messageForRecipient("\u20ac".repeat(140))
                .comment("\u20ac".repeat(140))
                .build();
        List<PaymentOrder> orders = new ArrayList<>();
        for (int order = 0; order < 2000; order++) {
            orders.add(longest);
        }
        return orders;
    }

    @Test
    void should_reject_without_request_naming_both_sizes_when_submitted_given_orders_larger_than_fio_imports() {
        PaymentBatch batch = new PaymentBatch(ordersLargerThanFioImports());

        assertThatIllegalArgumentException().isThrownBy(() -> client().payments().submit(batch))
                .withMessageMatching("The batch written as XML is \\d+ bytes, more than the 2097152 bytes the bank "
                        + "imports; submit it in smaller batches");
        assertThat(server().requests()).isEmpty();
    }

    @Test
    void should_refuse_as_state_without_request_and_keep_batch_open_when_submitted_given_orders_larger_than_fio_imports() {
        FioClient.BatchRequest batch = client().payments().batch();
        ordersLargerThanFioImports().forEach(batch::add);

        assertThatIllegalStateException().isThrownBy(batch::submit)
                .withMessageMatching("The batch written as XML is \\d+ bytes, more than the 2097152 bytes the bank "
                        + "imports; submit it in smaller batches")
                .withCauseInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> batch.add(domestic())).doesNotThrowAnyException();
        assertThat(server().requests()).isEmpty();
    }

    @Test
    void should_refuse_as_state_without_request_when_submitted_given_orders_from_two_accounts() {
        FioClient.BatchRequest batch = client().payments().batch()
                .add(domestic())
                .domestic(order -> order.from("2400222222/2010").to("19-2000145399/0800").amount(Money.czk("1")).date(DUE));

        assertThatIllegalStateException().isThrownBy(batch::submit)
                .withMessage("Every order in a batch is for the same account, the one the token belongs to: "
                        + "1234562 and 2400222222/2010")
                .withCauseInstanceOf(IllegalArgumentException.class);
        assertThat(server().requests()).isEmpty();
    }

    static Stream<Arguments> additions() {
        return Stream.of(
                argumentSet("domestic", (Consumer<FioClient.BatchRequest>) batch -> batch.domestic(order -> order
                        .from(PaymentSamples.PAYER).to("19-2000145399/0800").amount(Money.czk("1")).date(DUE))),
                argumentSet("euro", (Consumer<FioClient.BatchRequest>) batch -> batch.euro(order -> order
                        .from(PaymentSamples.PAYER).to("AT611904300234573201").amount(euro("1")).date(DUE).beneficiary("Hans"))),
                argumentSet("foreign", (Consumer<FioClient.BatchRequest>) batch -> batch.foreign(order -> order
                        .from(PaymentSamples.PAYER).to("PK36SCBL0000001123456702").bic("ALFHPKKAXXX")
                        .amount(Money.of("1", "USD")).date(DUE)
                        .beneficiary(new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK"))
                        .remittanceInfo("Hotel").paymentReason("348"))),
                argumentSet("ready order", (Consumer<FioClient.BatchRequest>) batch -> batch.add(domestic())));
    }

    @ParameterizedTest
    @MethodSource("additions")
    void should_refuse_the_order_without_another_request_when_added_given_submitted_batch(Consumer<FioClient.BatchRequest> addition) {
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));
        FioClient.BatchRequest batch = client().payments().batch().add(domestic());
        batch.submit();

        assertThatIllegalStateException().isThrownBy(() -> addition.accept(batch))
                .withMessage("The batch was already submitted; build a new one to add orders");
        assertThat(server().requests()).containsExactly(IMPORT_REQUEST);
    }

    @Test
    void should_raise_failure_with_status_and_masked_body_when_submitted_given_http_error_with_import_answer() {
        answer(400, "<responseImport><result><errorCode>11</errorCode><status>error</status>"
                + "<message>Syntaktická chyba, token " + SECRET + "</message></result></responseImport>");

        FioException failure = catchThrowableOfType(FioException.class,
                () -> client().payments().submit(PaymentBatch.of(domestic())));

        assertThat(failure).isExactlyInstanceOf(FioException.class)
                .hasMessageContainingAll("Fio API request POST ", "/v1/rest/import/ was refused with HTTP 400")
                .hasMessageNotContaining(SECRET);
        assertThat(failure.statusCode()).hasValue(400);
        assertThat(failure.responseBody()).hasValueSatisfying(body -> assertThat(body)
                .contains("<errorCode>11</errorCode>", "token ***")
                .doesNotContain(SECRET));
    }

    @Test
    void should_raise_authorization_failure_when_submitted_given_inactive_token() {
        answer(500, "");

        FioAuthorizationException failure = catchThrowableOfType(FioAuthorizationException.class,
                () -> client().payments().submit(PaymentBatch.of(domestic())));

        assertThat(failure).hasMessageContainingAll("POST ", "/v1/rest/import/", "HTTP 500", "could not read the request");
        assertThat(failure.statusCode()).hasValue(500);
    }

    @Test
    void should_raise_failure_without_token_when_submitted_given_ok_answer_this_client_does_not_understand() {
        answer(200, "<" + SECRET + ">Chyba</" + SECRET + ">");

        FioException failure = catchThrowableOfType(FioException.class,
                () -> client().payments().submit(PaymentBatch.of(domestic())));

        assertThat(failure).hasMessageContaining("does not understand: The body is a <***>, not a <responseImport>");
        assertThat(failure.responseBody()).hasValue("<***>Chyba</***>");
        assertThat(printed(failure)).doesNotContain(SECRET);
    }

    @Test
    void should_send_same_file_again_after_thirty_seconds_when_bank_refuses_given_rate_limit() {
        server().enqueue(409, XML, new byte[0]);
        answer(200, Fixtures.read(Fixtures.IMPORT_ACCEPTED));

        ImportResult result = client().payments().submit(PaymentBatch.of(domestic()));

        assertThat(result).isEqualTo(ACCEPTED);
        assertThat(server().requests()).containsExactly(IMPORT_REQUEST, IMPORT_REQUEST);
        assertThat(fileOf(server().bodies().get(1))).isEqualTo(fileOf(server().bodies().get(0)));
        assertThat(server().arrivals()).containsExactly(NOW, NOW.plusSeconds(30));
    }
}
