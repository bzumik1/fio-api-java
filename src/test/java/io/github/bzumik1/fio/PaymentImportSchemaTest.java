package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.PaymentSamples.domestic;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.PaymentSamples.foreign;
import static io.github.bzumik1.fio.PaymentSamples.fullDomestic;
import static io.github.bzumik1.fio.PaymentSamples.fullEuro;
import static io.github.bzumik1.fio.PaymentSamples.fullForeign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentImportSchemaTest {

    private final PaymentImportWriter writer = new PaymentImportWriter();

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

        assertThatCode(() -> ImportSchema.validate(ImportSchema.IMPORT, xml)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            FixtureBytes.DOMESTIC_PAYMENT_EXAMPLE, FixtureBytes.EURO_PAYMENT_EXAMPLE, FixtureBytes.FOREIGN_PAYMENT_EXAMPLE})
    void should_match_fio_import_schema_when_validated_given_payment_example_of_the_documentation(String example) {
        byte[] xml = FixtureBytes.read(example);

        assertThatCode(() -> ImportSchema.validate(ImportSchema.IMPORT, xml)).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_orders_out_of_schema_sequence_when_validated_given_hand_written_file() {
        String domestic = new String(writer.write(List.of(domestic())), StandardCharsets.UTF_8);
        String euro = new String(writer.write(List.of(euro())), StandardCharsets.UTF_8);
        String euroThenDomestic = euro.replace("</Orders>", between(domestic, "<Orders>", "</Orders>") + "</Orders>");

        assertThatThrownBy(() -> ImportSchema.validate(ImportSchema.IMPORT, euroThenDomestic.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("DomesticTransaction");
    }

    @ParameterizedTest
    @ValueSource(strings = {Fixtures.IMPORT_ACCEPTED, Fixtures.IMPORT_REFUSED})
    void should_match_fio_response_schema_when_validated_given_response_fixture(String fixture) {
        byte[] xml = FixtureBytes.read(fixture);

        assertThatCode(() -> ImportSchema.validate(ImportSchema.RESPONSE, xml)).doesNotThrowAnyException();
    }

    @Test
    void should_use_exactly_the_schema_codes_when_listed_given_domestic_payment_types() {
        assertThat(Stream.of(DomesticPayment.Type.values()).map(DomesticPayment.Type::code))
                .containsExactlyElementsOf(ImportSchema.enumeration("payment_type_domestic_type"));
    }

    @Test
    void should_use_exactly_the_schema_codes_when_listed_given_euro_payment_types() {
        assertThat(Stream.of(EuroPayment.Type.values()).map(EuroPayment.Type::code))
                .containsExactlyElementsOf(ImportSchema.enumeration("payment_type_t2_type"));
    }

    @Test
    void should_use_exactly_the_schema_codes_when_listed_given_foreign_payment_charges() {
        assertThat(Stream.of(ForeignPayment.Charges.values()).map(ForeignPayment.Charges::code))
                .containsExactlyElementsOf(ImportSchema.enumeration("details_charges_type"));
    }

    @Test
    void should_use_exactly_the_schema_statuses_when_listed_given_import_statuses() {
        assertThat(Stream.of(ImportResult.Status.values()).map(status -> status.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(ImportSchema.enumeration("status"));
    }

    private static String between(String text, String start, String end) {
        return text.substring(text.indexOf(start) + start.length(), text.indexOf(end));
    }
}
