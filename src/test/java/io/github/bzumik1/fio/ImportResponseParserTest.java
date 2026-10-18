package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;
import io.github.bzumik1.fio.ImportResult.Status;
import io.github.bzumik1.fio.ImportResult.Sum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.stream.XMLStreamException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportResponseParserTest {

    private final ImportResponseParser parser = new ImportResponseParser();

    private static String response(String result) {
        return "<responseImport><result>" + result + "</result></responseImport>";
    }

    @Test
    void should_read_batch_id_sums_and_order_messages_when_parsed_given_accepted_batch() {
        ImportResult result = parser.parse(Fixtures.read(Fixtures.IMPORT_ACCEPTED));

        assertThat(result).isEqualTo(new ImportResult(
                0,
                Status.OK,
                105699806L,
                List.of(new Sum("CZK", new BigDecimal("100.00"), new BigDecimal("0"))),
                List.of(new Message(1, Status.OK, "OK"))));
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void should_read_batch_message_before_order_messages_when_parsed_given_refused_batch() {
        ImportResult result = parser.parse(Fixtures.read(Fixtures.IMPORT_REFUSED));

        assertThat(result).isEqualTo(new ImportResult(
                1,
                Status.ERROR,
                null,
                List.of(new Sum("CZK", new BigDecimal("350.50"), new BigDecimal("0")),
                        new Sum("EUR", new BigDecimal("12.00"), new BigDecimal("0"))),
                List.of(new Message(null, null, "Nalezené chyby při kontrole příkazů"),
                        new Message(1, Status.OK, "OK"),
                        new Message(2, Status.ERROR, "Neplatný kód banky příjemce."),
                        new Message(2, Status.WARNING, "Měna platby neodpovídá měně účtu."))));
        assertThat(result.isAccepted()).isFalse();
    }

    @Test
    void should_read_nothing_more_when_parsed_given_only_error_code_and_status() {
        ImportResult result = parser.parse(response("<errorCode>12</errorCode><status>error</status>"));

        assertThat(result).isEqualTo(new ImportResult(12, Status.ERROR, null, List.of(), List.of()));
    }

    @Test
    void should_keep_missing_parts_empty_when_parsed_given_sum_detail_and_message_without_attributes() {
        ImportResult result = parser.parse("""
                <responseImport>
                  <result>
                    <errorCode>2</errorCode>
                    <status>warning</status>
                    <sums><sum/></sums>
                  </result>
                  <ordersDetails>
                    <detail><messages><message>Bez čísla</message></messages></detail>
                    <detail id="3"/>
                  </ordersDetails>
                </responseImport>
                """);

        assertThat(result).isEqualTo(new ImportResult(2, Status.WARNING, null, List.of(new Sum(null, null, null)),
                List.of(new Message(null, null, "Bez čísla"))));
    }

    @ParameterizedTest
    @CsvSource({"ok, OK", "warning, WARNING", "error, ERROR", "fatal, FATAL", "FATAL, FATAL", " ok , OK"})
    void should_read_status_ignoring_case_and_padding_when_parsed_given_status_text(String text, Status status) {
        assertThat(parser.parse(response("<errorCode>0</errorCode><status>" + text + "</status>")).status())
                .isEqualTo(status);
    }

    @Test
    void should_take_first_batch_id_when_parsed_given_several() {
        ImportResult result = parser.parse(response(
                "<errorCode>0</errorCode><idInstruction>7</idInstruction><idInstruction>8</idInstruction><status>ok</status>"));

        assertThat(result.instructionId()).isEqualTo(7L);
    }

    @Test
    void should_join_text_and_skip_comments_when_parsed_given_message_with_cdata_entities_and_comments() {
        ImportResult result = parser.parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- answer -->
                <?fio note?>
                <responseImport><result><errorCode>0</errorCode><status>ok</status>
                <message> <![CDATA[a < b]]> &amp; c<!-- hidden --> &#x10D;<?fio pi?></message>
                </result></responseImport>
                """);

        assertThat(result.messages()).containsExactly(new Message(null, null, "a < b & c č"));
    }

    @Test
    void should_read_local_names_when_parsed_given_namespaced_answer() {
        ImportResult result = parser.parse("""
                <r:responseImport xmlns:r="urn:fio"><r:result><r:errorCode>0</r:errorCode><r:status>ok</r:status>\
                </r:result></r:responseImport>""");

        assertThat(result.status()).isEqualTo(Status.OK);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"\"| The body is not well-formed XML without a DTD",
            "Service Unavailable| The body is not well-formed XML without a DTD",
            "<responseImport><result>| The body is not well-formed XML without a DTD",
            "<html><body>Chyba</body></html>| The body is a <html>, not a <responseImport>",
            "<responseImport/>| The <responseImport> has no <result>",
            "<responseImport><result><status>ok</status></result></responseImport>| The <result> has no <errorCode>",
            "<responseImport><result><errorCode>0</errorCode></result></responseImport>| The <result> has no <status>",
            "<responseImport><result><errorCode>x</errorCode><status>ok</status></result></responseImport>"
                    + "| The errorCode is not a number: 'x'",
            "<responseImport><result><errorCode>0</errorCode><status>done</status></result></responseImport>"
                    + "| The status is not ok, warning, error or fatal: 'done'",
            "<responseImport><result><errorCode>0</errorCode><idInstruction>1a</idInstruction><status>ok</status>"
                    + "</result></responseImport>| The idInstruction is not a number: '1a'",
            "<responseImport><result><errorCode>0</errorCode><status>ok</status><sums><sum><sumDebet>1,5</sumDebet>"
                    + "</sum></sums></result></responseImport>| The sumDebet is not a number: '1,5'",
            "<responseImport><result><errorCode>0</errorCode><status>ok</status><sums><sum><sumCredit></sumCredit>"
                    + "</sum></sums></result></responseImport>| The sumCredit is not a number: ''",
            "<responseImport><result><errorCode>0</errorCode><status>ok</status></result><ordersDetails>"
                    + "<detail id='první'/></ordersDetails></responseImport>| The detail id is not a number: 'první'",
            "<responseImport><result><errorCode>0</errorCode><status>ok</status></result><ordersDetails><detail id='1'>"
                    + "<messages><message status='bad'>x</message></messages></detail></ordersDetails></responseImport>"
                    + "| The message status is not ok, warning, error or fatal: 'bad'"
    })
    void should_reject_naming_the_problem_when_parsed_given_answer_this_client_does_not_understand(
            String body, String problem) {
        assertThatThrownBy(() -> parser.parse(body))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage(problem);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "http://127.0.0.1:1/entity"})
    void should_refuse_with_the_cause_when_parsed_given_internal_subset_with_external_entity(String entity) {
        String body = "<?xml version=\"1.0\"?>\n<!DOCTYPE responseImport [<!ENTITY secret SYSTEM \"" + entity + "\">]>\n"
                + "<responseImport><result><errorCode>0</errorCode><status>ok</status><message>&secret;</message>"
                + "</result></responseImport>";

        assertThatThrownBy(() -> parser.parse(body))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("The body is not well-formed XML without a DTD")
                .hasCauseInstanceOf(XMLStreamException.class);
    }

    @Test
    void should_refuse_without_fetching_anything_when_parsed_given_document_type_and_entities_on_a_server() {
        try (StubFioServer canary = StubFioServer.start()) {
            String body = "<?xml version=\"1.0\"?><!DOCTYPE responseImport SYSTEM \"" + canary.baseUri() + "/schema.dtd\" ["
                    + "<!ENTITY % parameter SYSTEM \"" + canary.baseUri() + "/parameter\"> %parameter;"
                    + "<!ENTITY remote SYSTEM \"" + canary.baseUri() + "/entity\">]>"
                    + "<responseImport><result><errorCode>0</errorCode><status>ok</status><message>&remote;</message>"
                    + "</result></responseImport>";

            assertThatThrownBy(() -> parser.parse(body))
                    .isExactlyInstanceOf(MalformedResponseException.class)
                    .hasMessage("The body is not well-formed XML without a DTD");
            assertThat(canary.requests()).isEmpty();
        }
    }

    @Test
    void should_reject_without_exhausting_the_stack_when_parsed_given_two_hundred_thousand_nested_elements() {
        String body = "<responseImport>" + "<x>".repeat(200_000) + "</x>".repeat(200_000) + "</responseImport>";

        assertThatThrownBy(() -> parser.parse(body))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("The body nests elements deeper than an import answer does");
    }

    @Test
    void should_read_answer_when_parsed_given_nesting_up_to_the_limit() {
        String nested = "<x>".repeat(13) + "</x>".repeat(13);

        assertThat(parser.parse(response("<errorCode>0</errorCode><status>ok</status>" + nested)).status()).isEqualTo(Status.OK);
    }

    @Test
    void should_reject_when_parsed_given_nesting_one_level_beyond_the_limit() {
        String nested = "<x>".repeat(15) + "</x>".repeat(15);

        assertThatThrownBy(() -> parser.parse(response("<errorCode>0</errorCode><status>ok</status>" + nested)))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("The body nests elements deeper than an import answer does");
    }

    static Stream<Arguments> encodedRefusals() {
        String body = Fixtures.read(Fixtures.IMPORT_REFUSED);
        String windows1250 = body.replace("encoding=\"UTF-8\"", "encoding=\"windows-1250\"");
        byte[] utf8 = body.getBytes(StandardCharsets.UTF_8);
        byte[] marked = new byte[utf8.length + 3];
        marked[0] = (byte) 0xEF;
        marked[1] = (byte) 0xBB;
        marked[2] = (byte) 0xBF;
        System.arraycopy(utf8, 0, marked, 3, utf8.length);
        return Stream.of(
                Arguments.of("windows-1250 declared", windows1250.getBytes(Charset.forName("windows-1250"))),
                Arguments.of("UTF-8 with a byte order mark", marked),
                Arguments.of("UTF-16 declared with a byte order mark",
                        body.replace("encoding=\"UTF-8\"", "encoding=\"UTF-16\"").getBytes(StandardCharsets.UTF_16)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("encodedRefusals")
    void should_read_czech_messages_when_parsed_given_body_in_the_encoding_its_declaration_or_mark_names(String encoding, byte[] body) {
        assertThat(parser.parse(body).messages()).extracting(Message::text).containsExactly(
                "Nalezené chyby při kontrole příkazů", "OK", "Neplatný kód banky příjemce.", "Měna platby neodpovídá měně účtu.");
    }

    @ParameterizedTest
    @CsvSource({"+1.50, 1.50", ".5, 0.5", "12., 12"})
    void should_read_sum_when_parsed_given_decimal_in_any_form_the_schema_allows(String text, BigDecimal expected) {
        ImportResult result = parser.parse(response(
                "<errorCode>0</errorCode><status>ok</status><sums><sum id=\"CZK\"><sumDebet>" + text + "</sumDebet></sum></sums>"));

        assertThat(result.sums()).containsExactly(new Sum("CZK", expected, null));
    }

    @ParameterizedTest
    @MethodSource("io.github.bzumik1.fio.DecimalBounds#beyond")
    void should_reject_without_building_the_number_when_parsed_given_sum_with_too_many_digits(String text) {
        String body = response("<errorCode>0</errorCode><status>ok</status><sums><sum><sumDebet>" + text
                + "</sumDebet></sum></sums>");

        assertThatThrownBy(() -> parser.parse(body))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessageStartingWith("The sumDebet is not a number: '");
    }

    @ParameterizedTest
    @MethodSource("io.github.bzumik1.fio.DecimalBounds#within")
    void should_keep_every_digit_when_parsed_given_sum_at_the_digit_bounds(String text) {
        ImportResult result = parser.parse(response(
                "<errorCode>0</errorCode><status>ok</status><sums><sum id=\"CZK\"><sumCredit>" + text + "</sumCredit></sum></sums>"));

        assertThat(result.sums()).containsExactly(new Sum("CZK", null, new BigDecimal(text)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e3", "1E999999999", "NaN", "0x1"})
    void should_reject_naming_the_sum_when_parsed_given_sum_that_is_not_a_plain_decimal(String text) {
        String body = response("<errorCode>0</errorCode><status>ok</status><sums><sum><sumCredit>" + text
                + "</sumCredit></sum></sums>");

        assertThatThrownBy(() -> parser.parse(body))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("The sumCredit is not a number: '" + text + "'");
    }
}
