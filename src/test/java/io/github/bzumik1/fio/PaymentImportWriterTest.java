package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.bzumik1.fio.PaymentSamples.domestic;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.PaymentSamples.foreign;
import static io.github.bzumik1.fio.PaymentSamples.fullDomestic;
import static io.github.bzumik1.fio.PaymentSamples.fullEuro;
import static io.github.bzumik1.fio.PaymentSamples.fullForeign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class PaymentImportWriterTest {

    private static final String PROLOG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Import xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:noNamespaceSchemaLocation=\"http://www.fio.cz/schema/importIB.xsd\"><Orders>";
    private static final String EPILOG = "</Orders></Import>";

    private static final LocalDate EXAMPLE_DATE = LocalDate.of(2013, 4, 25);

    private final PaymentImportWriter writer = new PaymentImportWriter();

    static String compact(String xml) {
        return xml.lines().map(String::strip).collect(Collectors.joining());
    }

    private String written(PaymentOrder... orders) {
        return new String(writer.write(List.of(orders)), StandardCharsets.UTF_8);
    }

    @Test
    void should_write_every_field_in_schema_order_when_written_given_full_domestic_payment() {
        assertThat(written(fullDomestic())).isEqualTo(PROLOG + compact("""
                <DomesticTransaction>
                  <accountFrom>1234562</accountFrom>
                  <currency>CZK</currency>
                  <amount>100.00</amount>
                  <accountTo>19-2000145399</accountTo>
                  <bankCode>0800</bankCode>
                  <ks>0558</ks>
                  <vs>1234567890</vs>
                  <ss>0000000002</ss>
                  <date>2026-10-20</date>
                  <messageForRecipient>Hračky pro děti v PENNY MARKET</messageForRecipient>
                  <comment>Dárky &amp; &lt;hračky&gt;</comment>
                  <paymentReason>110</paymentReason>
                  <paymentType>431005</paymentType>
                </DomesticTransaction>
                """) + EPILOG);
    }

    @Test
    void should_leave_out_optional_fields_when_written_given_minimal_domestic_payment() {
        assertThat(written(domestic())).isEqualTo(PROLOG + compact("""
                <DomesticTransaction>
                  <accountFrom>1234562</accountFrom>
                  <currency>CZK</currency>
                  <amount>100.00</amount>
                  <accountTo>19-2000145399</accountTo>
                  <bankCode>0800</bankCode>
                  <date>2026-10-20</date>
                  <paymentType>431001</paymentType>
                </DomesticTransaction>
                """) + EPILOG);
    }

    @Test
    void should_write_every_field_in_schema_order_when_written_given_full_euro_payment() {
        assertThat(written(fullEuro())).isEqualTo(PROLOG + compact("""
                <T2Transaction>
                  <accountFrom>1234562</accountFrom>
                  <currency>EUR</currency>
                  <amount>100.00</amount>
                  <accountTo>AT611904300234573201</accountTo>
                  <ks>0558</ks>
                  <vs>1234567890</vs>
                  <ss>1234567890</ss>
                  <bic>ABAGATWWXXX</bic>
                  <date>2026-10-20</date>
                  <comment>Erste Zahlung</comment>
                  <benefName>Hans Gruber</benefName>
                  <benefStreet>Gugitzgasse 2</benefStreet>
                  <benefCity>Wien</benefCity>
                  <benefCountry>AT</benefCountry>
                  <remittanceInfo1>Rechnung 2026-17</remittanceInfo1>
                  <remittanceInfo2>Kunde 4711</remittanceInfo2>
                  <remittanceInfo3>Danke</remittanceInfo3>
                  <paymentReason>110</paymentReason>
                  <paymentType>431018</paymentType>
                </T2Transaction>
                """) + EPILOG);
    }

    @Test
    void should_leave_out_optional_fields_when_written_given_minimal_euro_payment() {
        assertThat(written(euro())).isEqualTo(PROLOG + compact("""
                <T2Transaction>
                  <accountFrom>1234562</accountFrom>
                  <currency>EUR</currency>
                  <amount>100.00</amount>
                  <accountTo>AT611904300234573201</accountTo>
                  <date>2026-10-20</date>
                  <benefName>Hans Gruber</benefName>
                  <paymentType>431008</paymentType>
                </T2Transaction>
                """) + EPILOG);
    }

    @Test
    void should_write_every_field_in_schema_order_when_written_given_full_foreign_payment() {
        assertThat(written(fullForeign())).isEqualTo(PROLOG + compact("""
                <ForeignTransaction>
                  <accountFrom>1234562</accountFrom>
                  <currency>USD</currency>
                  <amount>100.00</amount>
                  <accountTo>PK36SCBL0000001123456702</accountTo>
                  <bic>ALFHPKKAXXX</bic>
                  <date>2026-10-20</date>
                  <comment>Payment a0315</comment>
                  <benefName>Amir Khan</benefName>
                  <benefStreet>Nishtar Rd 13</benefStreet>
                  <benefCity>Karachi</benefCity>
                  <benefCountry>PK</benefCountry>
                  <remittanceInfo1>Payment for hotel 032013</remittanceInfo1>
                  <remittanceInfo2>Room 12</remittanceInfo2>
                  <remittanceInfo3>Two nights</remittanceInfo3>
                  <remittanceInfo4>Thank you</remittanceInfo4>
                  <detailsOfCharges>470502</detailsOfCharges>
                  <paymentReason>348</paymentReason>
                </ForeignTransaction>
                """) + EPILOG);
    }

    @Test
    void should_write_charges_shared_by_default_when_written_given_minimal_foreign_payment() {
        assertThat(written(foreign())).contains(compact("""
                <remittanceInfo1>Payment for hotel 032013</remittanceInfo1>
                <detailsOfCharges>470503</detailsOfCharges>
                <paymentReason>348</paymentReason>
                </ForeignTransaction>
                """)).doesNotContain("<comment>", "remittanceInfo2");
    }

    @Test
    void should_write_domestic_then_euro_then_foreign_keeping_sequence_within_kind_when_written_given_mixed_orders() {
        DomesticPayment firstDomestic = domestic();
        DomesticPayment secondDomestic = PaymentSamples.domesticBuilder().variableSymbol("2").build();

        String xml = written(foreign(), firstDomestic, euro(), secondDomestic);

        assertThat(xml).isEqualTo(PROLOG + order(firstDomestic) + order(secondDomestic) + order(euro()) + order(foreign()) + EPILOG);
    }

    private String order(PaymentOrder order) {
        String file = written(order);
        return file.substring(PROLOG.length(), file.length() - EPILOG.length());
    }

    @Test
    void should_list_batch_positions_in_file_sequence_when_file_order_asked_given_mixed_orders() {
        List<PaymentOrder> orders = List.of(foreign(), euro(), domestic(), foreign(), domestic(), euro());

        assertThat(PaymentImportWriter.fileOrder(orders)).containsExactly(2, 4, 1, 5, 0, 3);
    }

    @ParameterizedTest
    @CsvSource({"1E+3, 1000.00", "0.500, 0.50", "7, 7.00", "9999999999999999.99, 9999999999999999.99"})
    void should_write_amount_with_two_decimals_when_written_given_amount_of_other_scale(BigDecimal amount, String written) {
        assertThat(written(PaymentSamples.domesticBuilder().amount(Money.czk(amount)).build()))
                .contains("<amount>" + written + "</amount>");
    }

    @Test
    void should_write_account_number_without_bank_code_when_written_given_payer_with_fio_bank_code() {
        assertThat(written(PaymentSamples.domesticBuilder().from("2400222222/2010").build()))
                .contains("<accountFrom>2400222222</accountFrom>");
    }

    @Test
    void should_encode_as_utf8_when_written_given_czech_text() {
        byte[] file = writer.write(List.of(fullDomestic()));

        assertThat(file).containsSequence("Hračky".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_write_the_documentation_example_with_a_checksum_valid_recipient_when_written_given_its_domestic_order_whose_recipient_fails_modulo_11() {
        DomesticPayment order = DomesticPayment.builder()
                .from("1234562")
                .to("19-2000145399/0800")
                .amount(Money.czk("100.00"))
                .constantSymbol("0558")
                .variableSymbol("1234567890")
                .specificSymbol("1234567890")
                .date(EXAMPLE_DATE)
                .messageForRecipient("Hračky pro děti v PENNY\nMARKET")
                .type(DomesticPayment.Type.STANDARD)
                .build();
        List<String> expected = ImportSchema.nonEmptyLeaves(FixtureBytes.read(FixtureBytes.DOMESTIC_PAYMENT_EXAMPLE)).stream()
                .map(leaf -> leaf.replace("accountTo=2212-2000000699", "accountTo=19-2000145399").replace("bankCode=0300", "bankCode=0800"))
                .collect(Collectors.toList());

        assertThat(ImportSchema.nonEmptyLeaves(writer.write(List.of(order)))).containsExactlyElementsOf(expected);
    }

    @Test
    void should_fail_the_modulo_11_check_when_checked_given_recipient_of_the_documentation_domestic_example() {
        assertThat(DomesticAccount.parse("2212-2000000699/0300").hasValidChecksum()).isFalse();
    }

    @Test
    void should_write_the_documentation_example_when_written_given_its_euro_order() {
        EuroPayment order = EuroPayment.builder()
                .from("1234562")
                .to("AT611904300234573201")
                .amount(Money.of("100.00", "EUR"))
                .constantSymbol("0558")
                .variableSymbol("1234567890")
                .specificSymbol("1234567890")
                .bic("ABAGATWWXXX")
                .date(EXAMPLE_DATE)
                .comment("Erste Zahlung")
                .beneficiary(new Beneficiary("Hans Gruber", "Gugitzgasse 2", "Wien", "AT"))
                .type(EuroPayment.Type.STANDARD)
                .build();

        assertThat(ImportSchema.nonEmptyLeaves(writer.write(List.of(order))))
                .containsExactlyElementsOf(ImportSchema.nonEmptyLeaves(FixtureBytes.read(FixtureBytes.EURO_PAYMENT_EXAMPLE)));
    }

    @Test
    void should_write_the_documentation_example_when_written_given_its_foreign_order() {
        ForeignPayment order = ForeignPayment.builder()
                .from("1234562")
                .to("PK36SCBL0000001123456702")
                .bic("ALFHPKKAXXX")
                .amount(Money.of("100.00", "USD"))
                .date(EXAMPLE_DATE)
                .comment("Payment a0315")
                .beneficiary(new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK"))
                .remittanceInfo("Payment for hotel 032013")
                .charges(ForeignPayment.Charges.BENEFICIARY)
                .paymentReason("348")
                .build();

        assertThat(ImportSchema.nonEmptyLeaves(writer.write(List.of(order))))
                .containsExactlyElementsOf(ImportSchema.nonEmptyLeaves(FixtureBytes.read(FixtureBytes.FOREIGN_PAYMENT_EXAMPLE)));
    }

    @Test
    void should_fail_with_cause_when_written_given_output_that_cannot_be_written() {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };

        assertThatIllegalStateException()
                .isThrownBy(() -> writer.write(List.of(domestic()), broken))
                .withMessage("The payment orders could not be written as Fio XML")
                .withRootCauseInstanceOf(IOException.class);
    }
}
