package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.PaymentSamples.domestic;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.PaymentSamples.foreign;
import static io.github.bzumik1.fio.PaymentSamples.fullDomestic;
import static io.github.bzumik1.fio.PaymentSamples.fullEuro;
import static io.github.bzumik1.fio.PaymentSamples.fullForeign;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentImportSchemaTest {

    private static final Schema IMPORT = schema("importIB.xsd");
    private static final Schema RESPONSE = schema("responseImportIB.xsd");

    private final PaymentImportWriter writer = new PaymentImportWriter();

    private static Schema schema(String name) {
        URL location = PaymentImportSchemaTest.class.getResource("/schema/" + name);
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            return factory.newSchema(location);
        } catch (SAXException e) {
            throw new IllegalStateException("The vendored schema " + name + " does not load", e);
        }
    }

    private static void validate(Schema schema, byte[] xml) throws SAXException {
        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        try {
            validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Stream<List<PaymentOrder>> batches() {
        String longest140 = "č".repeat(140);
        String longest35 = "x".repeat(35);
        return Stream.of(
                List.of(domestic()),
                List.of(fullDomestic()),
                List.of(euro()),
                List.of(fullEuro()),
                List.of(foreign()),
                List.of(fullForeign()),
                List.of(fullForeign(), euro(), fullDomestic(), foreign(), domestic(), fullEuro()),
                List.of(PaymentSamples.domesticBuilder()
                        .messageForRecipient(longest140)
                        .comment(longest140)
                        .type(DomesticPayment.Type.DIRECT_DEBIT)
                        .build()),
                List.of(PaymentSamples.euroBuilder()
                        .comment(longest140)
                        .beneficiary(new Beneficiary(longest35, longest35, longest35, "SK"))
                        .remittanceInfo(longest35)
                        .type(EuroPayment.Type.PRIORITY)
                        .build()),
                List.of(PaymentSamples.foreignBuilder()
                        .to("AB12" + "3".repeat(30))
                        .bic("ALFHPKKA")
                        .comment(longest140)
                        .charges(ForeignPayment.Charges.PAYER)
                        .build()));
    }

    @ParameterizedTest
    @MethodSource("batches")
    void should_match_fio_import_schema_when_written_given_batch(List<PaymentOrder> orders) {
        byte[] xml = writer.write(orders);

        assertThatCode(() -> validate(IMPORT, xml)).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_orders_out_of_schema_sequence_when_validated_given_hand_written_file() {
        String domestic = new String(writer.write(List.of(domestic())), StandardCharsets.UTF_8);
        String euro = new String(writer.write(List.of(euro())), StandardCharsets.UTF_8);
        String euroThenDomestic = euro.replace("</Orders>", between(domestic, "<Orders>", "</Orders>") + "</Orders>");

        assertThatThrownBy(() -> validate(IMPORT, euroThenDomestic.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("DomesticTransaction");
    }

    @ParameterizedTest
    @ValueSource(strings = {Fixtures.IMPORT_ACCEPTED, Fixtures.IMPORT_REFUSED})
    void should_match_fio_response_schema_when_validated_given_response_fixture(String fixture) {
        byte[] xml = Fixtures.read(fixture).getBytes(StandardCharsets.UTF_8);

        assertThatCode(() -> validate(RESPONSE, xml)).doesNotThrowAnyException();
    }

    private static String between(String text, String start, String end) {
        return text.substring(text.indexOf(start) + start.length(), text.indexOf(end));
    }
}
