package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;
import io.github.bzumik1.fio.ImportResult.Status;
import io.github.bzumik1.fio.ImportResult.Sum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.xml.stream.XMLStreamException;
import java.math.BigDecimal;
import java.util.List;

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

        assertThat(result.sums()).containsExactly(new Sum(null, null, null));
        assertThat(result.messages()).containsExactly(new Message(null, null, "Bez čísla"));
        assertThat(result.isAccepted()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"ok, OK", "warning, WARNING", "error, ERROR", "fatal, FATAL", "FATAL, FATAL", " ok , OK"})
    void should_read_status_ignoring_case_and_padding_when_parsed(String text, Status status) {
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

    @Test
    void should_refuse_without_reading_files_when_parsed_given_external_entity() {
        String body = """
                <?xml version="1.0"?>
                <!DOCTYPE responseImport [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <responseImport><result><errorCode>0</errorCode><status>ok</status><message>&secret;</message>\
                </result></responseImport>""";

        assertThatThrownBy(() -> parser.parse(body))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("The body is not well-formed XML without a DTD")
                .hasCauseInstanceOf(XMLStreamException.class);
    }
}
