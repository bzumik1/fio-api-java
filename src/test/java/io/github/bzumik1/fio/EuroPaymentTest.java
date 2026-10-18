package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.PaymentSamples.DUE;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.PaymentSamples.euroBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EuroPaymentTest {

    private static final DomesticAccount PAYER = DomesticAccount.parse("1234562");
    private static final ForeignAccount IBAN = new ForeignAccount("AT611904300234573201");
    private static final Beneficiary HANS = Beneficiary.named("Hans");

    static Stream<Arguments> nullComponents() {
        PaymentSymbols none = PaymentSymbols.none();
        Money amount = euro("1");
        List<String> lines = List.of();
        EuroPayment.Type standard = EuroPayment.Type.STANDARD;
        return Stream.of(
                argumentSet("from", "from", (Supplier<EuroPayment>) () ->
                        new EuroPayment(null, IBAN, null, amount, none, DUE, null, HANS, lines, null, standard)),
                argumentSet("to", "to", (Supplier<EuroPayment>) () ->
                        new EuroPayment(PAYER, null, null, amount, none, DUE, null, HANS, lines, null, standard)),
                argumentSet("amount", "amount", (Supplier<EuroPayment>) () ->
                        new EuroPayment(PAYER, IBAN, null, null, none, DUE, null, HANS, lines, null, standard)),
                argumentSet("symbols", "symbols", (Supplier<EuroPayment>) () ->
                        new EuroPayment(PAYER, IBAN, null, amount, null, DUE, null, HANS, lines, null, standard)),
                argumentSet("date", "date", (Supplier<EuroPayment>) () ->
                        new EuroPayment(PAYER, IBAN, null, amount, none, null, null, HANS, lines, null, standard)),
                argumentSet("beneficiary", "beneficiary", (Supplier<EuroPayment>) () ->
                        new EuroPayment(PAYER, IBAN, null, amount, none, DUE, null, null, lines, null, standard)),
                argumentSet("remittanceInfo", "remittanceInfo", (Supplier<EuroPayment>) () ->
                        new EuroPayment(PAYER, IBAN, null, amount, none, DUE, null, HANS, null, null, standard)),
                argumentSet("type", "type", (Supplier<EuroPayment>) () ->
                        new EuroPayment(PAYER, IBAN, null, amount, none, DUE, null, HANS, lines, null, null)));
    }

    @ParameterizedTest
    @MethodSource("nullComponents")
    void should_reject_naming_the_component_when_created_given_null(String component, Supplier<EuroPayment> create) {
        assertThatNullPointerException().isThrownBy(create::get).withMessage(component);
    }

    @Test
    void should_carry_every_part_when_built_given_full_order() {
        assertThat(PaymentSamples.fullEuro()).isEqualTo(new EuroPayment(
                DomesticAccount.parse("1234562"),
                new ForeignAccount("AT611904300234573201"),
                "ABAGATWWXXX",
                euro("100.00"),
                new PaymentSymbols("1234567890", "0558", "1234567890"),
                DUE,
                "Erste Zahlung",
                new Beneficiary("Hans Gruber", "Gugitzgasse 2", "Wien", "AT"),
                List.of("Rechnung 2026-17", "Kunde 4711", "Danke"),
                "110",
                EuroPayment.Type.INSTANT));
    }

    @Test
    void should_default_to_standard_order_without_optional_parts_when_built_given_required_parts_only() {
        assertThat(PaymentSamples.euro())
                .returns(EuroPayment.Type.STANDARD, EuroPayment::type)
                .returns(null, EuroPayment::bic)
                .returns(null, EuroPayment::comment)
                .returns(List.of(), EuroPayment::remittanceInfo)
                .returns(null, EuroPayment::paymentReason)
                .returns(Beneficiary.named("Hans Gruber"), EuroPayment::beneficiary)
                .returns(PaymentSymbols.none(), EuroPayment::symbols);
    }

    @Test
    void should_normalise_iban_and_bic_when_built_given_written_forms() {
        EuroPayment payment = euroBuilder()
                .from(DomesticAccount.of("2400222222", "2010"))
                .to("at61 1904 3002 3457 3201")
                .bic(" abagatww ")
                .build();

        assertThat(payment)
                .returns(new ForeignAccount("AT611904300234573201"), EuroPayment::to)
                .returns("ABAGATWW", EuroPayment::bic);
    }

    @ParameterizedTest
    @CsvSource({"STANDARD, 431008", "PRIORITY, 431009", "INSTANT, 431018"})
    void should_name_fio_code_when_asked_given_type(EuroPayment.Type type, String code) {
        assertThat(type.code()).isEqualTo(code);
    }

    static Stream<Arguments> incompleteOrders() {
        return Stream.of(
                Arguments.of("payer's account", (Supplier<EuroPayment>) () -> EuroPayment.builder().build()),
                Arguments.of("recipient's account", (Supplier<EuroPayment>) () -> EuroPayment.builder().from("1234562").build()),
                Arguments.of("amount", (Supplier<EuroPayment>) () -> EuroPayment.builder().from("1234562")
                        .to("AT611904300234573201").build()),
                Arguments.of("date", (Supplier<EuroPayment>) () -> EuroPayment.builder().from("1234562")
                        .to("AT611904300234573201").amount(euro("1")).build()),
                Arguments.of("beneficiary", (Supplier<EuroPayment>) () -> EuroPayment.builder().from("1234562")
                        .to("AT611904300234573201").amount(euro("1")).date(DUE).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteOrders")
    void should_name_missing_part_when_built_given_incomplete_order(String part, Supplier<EuroPayment> build) {
        assertThatIllegalStateException().isThrownBy(build::get).withMessage("The " + part + " is required");
    }

    @Test
    void should_reject_when_built_given_amount_not_in_euro() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().amount(Money.czk("100")).build())
                .withMessage("A SEPA payment is in euro, not CZK");
    }

    @Test
    void should_reject_when_built_given_czech_account_number_instead_of_iban() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().to("2000145399/0800").build())
                .withMessage("Not an IBAN: '2000145399/0800'");
    }

    @Test
    void should_reject_when_built_given_iban_with_wrong_check_digits() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().to("AT621904300234573201").build())
                .withMessage("Not an IBAN, the check digits do not match: 'AT621904300234573201'");
    }

    @Test
    void should_reject_when_built_given_malformed_bic() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().bic("ABAG").build())
                .withMessage("Not a BIC: 'ABAG'");
    }

    @Test
    void should_reject_when_built_given_payer_with_prefix() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().from("19-2400222222").build())
                .withMessage("The payer's account is a Fio account, which has no prefix: 19-2400222222");
    }

    @Test
    void should_reject_when_built_given_due_date_in_year_zero() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().date(LocalDate.of(0, 12, 31)).build())
                .withMessage("A due date has a four-digit year: 0000-12-31");
    }

    @Test
    void should_keep_three_lines_when_built_given_three_remittance_lines_of_35_characters() {
        String longest = "x".repeat(35);

        assertThat(euroBuilder().remittanceInfo(longest, longest, longest).build()
                .remittanceInfo()).containsExactly(longest, longest, longest);
    }

    @Test
    void should_reject_when_built_given_fourth_remittance_line() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> euroBuilder().remittanceInfo("1", "2", "3", "4").build())
                .withMessage("At most 3 remittance lines are allowed, not 4");
    }

    @Test
    void should_reject_when_built_given_remittance_line_longer_than_35_characters() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().remittanceInfo("x".repeat(36)).build())
                .withMessage("The remittance line is at most 35 characters, not 36");
    }

    @Test
    void should_reject_when_built_given_blank_remittance_line() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().remittanceInfo(" ").build())
                .withMessage("The remittance line is blank");
    }

    @Test
    void should_keep_own_copy_of_remittance_lines_when_created_given_mutable_list() {
        List<String> lines = new ArrayList<>(List.of(" Rechnung 17 "));
        EuroPayment payment = new EuroPayment(DomesticAccount.parse("1234562"), new ForeignAccount("AT611904300234573201"), null,
                euro("1"), PaymentSymbols.none(), DUE, null, Beneficiary.named("Hans"), lines, null, EuroPayment.Type.STANDARD);

        lines.add("later");

        assertThat(payment.remittanceInfo()).containsExactly("Rechnung 17");
        assertThatThrownBy(() -> payment.remittanceInfo().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_reject_when_built_given_comment_longer_than_fio_imports() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().comment("x".repeat(141)).build())
                .withMessage("The comment is at most 140 characters, not 141");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "999", "11"})
    void should_reject_when_built_given_payment_reason_outside_fio_list(String reason) {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().paymentReason(reason).build())
                .withMessage("Not a payment reason code Fio imports: '" + reason + "'");
    }

    @Test
    void should_reject_when_built_given_beneficiary_country_outside_fio_list() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> euroBuilder().beneficiary(new Beneficiary("Hans Gruber", null, null, "TCH")).build())
                .withMessage("Not a country code Fio imports: 'TCH'");
    }
}
