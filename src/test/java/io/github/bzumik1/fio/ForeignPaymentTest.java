package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.PaymentSamples.DUE;
import static io.github.bzumik1.fio.PaymentSamples.foreignBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class ForeignPaymentTest {

    private static final DomesticAccount PAYER = DomesticAccount.parse("1234562");
    private static final ForeignAccount IBAN = new ForeignAccount("PK36SCBL0000001123456702");
    private static final Beneficiary KHAN = new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK");

    static Stream<Arguments> nullComponents() {
        Money amount = Money.of("1", "USD");
        List<String> lines = List.of("Hotel");
        ForeignPayment.Charges shared = ForeignPayment.Charges.SHARED;
        return Stream.of(
                argumentSet("from", "from", (Supplier<ForeignPayment>) () ->
                        new ForeignPayment(null, IBAN, "ALFHPKKA", amount, DUE, null, KHAN, lines, shared, "348")),
                argumentSet("to", "to", (Supplier<ForeignPayment>) () ->
                        new ForeignPayment(PAYER, null, "ALFHPKKA", amount, DUE, null, KHAN, lines, shared, "348")),
                argumentSet("bic", "bic", (Supplier<ForeignPayment>) () ->
                        new ForeignPayment(PAYER, IBAN, null, amount, DUE, null, KHAN, lines, shared, "348")),
                argumentSet("amount", "amount", (Supplier<ForeignPayment>) () ->
                        new ForeignPayment(PAYER, IBAN, "ALFHPKKA", null, DUE, null, KHAN, lines, shared, "348")),
                argumentSet("date", "date", (Supplier<ForeignPayment>) () ->
                        new ForeignPayment(PAYER, IBAN, "ALFHPKKA", amount, null, null, KHAN, lines, shared, "348")),
                argumentSet("beneficiary", "beneficiary", (Supplier<ForeignPayment>) () ->
                        new ForeignPayment(PAYER, IBAN, "ALFHPKKA", amount, DUE, null, null, lines, shared, "348")),
                argumentSet("remittanceInfo", "remittanceInfo", (Supplier<ForeignPayment>) () ->
                        new ForeignPayment(PAYER, IBAN, "ALFHPKKA", amount, DUE, null, KHAN, null, shared, "348")),
                argumentSet("charges", "charges", (Supplier<ForeignPayment>) () ->
                        new ForeignPayment(PAYER, IBAN, "ALFHPKKA", amount, DUE, null, KHAN, lines, null, "348")));
    }

    @ParameterizedTest
    @MethodSource("nullComponents")
    void should_reject_naming_the_component_when_created_given_null(String component, Supplier<ForeignPayment> create) {
        assertThatNullPointerException().isThrownBy(create::get).withMessage(component);
    }

    private static final Beneficiary AMIR = new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK");
    private static final String ACCOUNT_OF_34_CHARACTERS = "AB12" + "3".repeat(30);

    @Test
    void should_carry_every_part_when_built_given_full_order() {
        assertThat(PaymentSamples.fullForeign()).isEqualTo(new ForeignPayment(
                DomesticAccount.parse("1234562"),
                new ForeignAccount("PK36SCBL0000001123456702"),
                "ALFHPKKAXXX",
                new Money(new BigDecimal("100"), Currency.getInstance("USD")),
                DUE,
                "Payment a0315",
                AMIR,
                List.of("Payment for hotel 032013", "Room 12", "Two nights", "Thank you"),
                ForeignPayment.Charges.BENEFICIARY,
                "348"));
    }

    @Test
    void should_default_to_shared_charges_without_comment_when_built_given_required_parts_only() {
        assertThat(PaymentSamples.foreign())
                .returns(ForeignPayment.Charges.SHARED, ForeignPayment::charges)
                .returns(null, ForeignPayment::comment)
                .returns(List.of("Payment for hotel 032013"), ForeignPayment::remittanceInfo);
    }

    @Test
    void should_drop_spaces_before_measuring_when_built_given_account_written_in_groups() {
        String grouped = ACCOUNT_OF_34_CHARACTERS.replaceAll("(.{4})", "$1 ");

        ForeignPayment payment = foreignBuilder().from(DomesticAccount.of("2400222222", "2010")).to(grouped).build();

        assertThat(payment.to()).isEqualTo(new ForeignAccount(ACCOUNT_OF_34_CHARACTERS));
    }

    @Test
    void should_reject_when_built_given_account_longer_than_34_characters() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().to(ACCOUNT_OF_34_CHARACTERS + "4").build())
                .withMessage("The recipient's account is at most 34 characters, not 35");
    }

    @Test
    void should_reject_when_built_given_blank_account() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().to("   ").build())
                .withMessage("A foreign account needs an identifier");
    }

    @ParameterizedTest
    @CsvSource({"PAYER, 470501", "BENEFICIARY, 470502", "SHARED, 470503"})
    void should_name_fio_code_when_asked_given_charges(ForeignPayment.Charges charges, String code) {
        assertThat(charges.code()).isEqualTo(code);
    }

    private static ForeignPayment.Builder started() {
        return ForeignPayment.builder().from("1234562").to("PK36").bic("ALFHPKKA");
    }

    static Stream<Arguments> incompleteOrders() {
        return Stream.of(
                Arguments.of("payer's account", (Supplier<ForeignPayment>) () -> ForeignPayment.builder().build()),
                Arguments.of("recipient's account", (Supplier<ForeignPayment>) () -> ForeignPayment.builder().from("1234562").build()),
                Arguments.of("BIC", (Supplier<ForeignPayment>) () -> ForeignPayment.builder().from("1234562").to("PK36").build()),
                Arguments.of("amount", (Supplier<ForeignPayment>) () -> started().build()),
                Arguments.of("date", (Supplier<ForeignPayment>) () -> started().amount(Money.czk("1")).build()),
                Arguments.of("beneficiary", (Supplier<ForeignPayment>) () -> started().amount(Money.czk("1")).date(DUE).build()),
                Arguments.of("payment reason", (Supplier<ForeignPayment>) () -> started().amount(Money.czk("1")).date(DUE)
                        .beneficiary(AMIR).remittanceInfo("x").build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteOrders")
    void should_name_missing_part_when_built_given_incomplete_order(String part, Supplier<ForeignPayment> build) {
        assertThatIllegalStateException().isThrownBy(build::get).withMessage("The " + part + " is required");
    }

    @Test
    void should_reject_when_created_given_no_payment_reason() {
        assertThatNullPointerException().isThrownBy(() -> new ForeignPayment(DomesticAccount.parse("1234562"),
                new ForeignAccount("PK36SCBL0000001123456702"), "ALFHPKKA", Money.czk("1"), DUE, null, AMIR, List.of("x"),
                ForeignPayment.Charges.SHARED, null)).withMessage("paymentReason");
    }

    @Test
    void should_reject_when_built_given_beneficiary_without_full_address() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> foreignBuilder().beneficiary(new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", null)).build())
                .withMessage("A payment outside SEPA needs the beneficiary's street, city and country");
    }

    @Test
    void should_reject_when_built_given_no_remittance_line() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> started().amount(Money.czk("1")).date(DUE).beneficiary(AMIR).paymentReason("348").build())
                .withMessage("A payment outside SEPA needs at least one remittance line");
    }

    @Test
    void should_reject_when_built_given_fifth_remittance_line() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> foreignBuilder().remittanceInfo("1", "2", "3", "4", "5").build())
                .withMessage("At most 4 remittance lines are allowed, not 5");
    }

    @Test
    void should_reject_when_built_given_malformed_bic() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().bic("12345678").build())
                .withMessage("Not a BIC: '12345678'");
    }

    @Test
    void should_reject_when_built_given_negative_amount() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().amount(Money.czk("-5")).build())
                .withMessage("A payment amount is positive: -5 CZK");
    }

    @Test
    void should_reject_when_built_given_comment_longer_than_fio_imports() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().comment("x".repeat(141)).build())
                .withMessage("The comment is at most 140 characters, not 141");
    }

    @Test
    void should_reject_when_built_given_payment_reason_outside_fio_list() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().paymentReason("34").build())
                .withMessage("Not a payment reason code Fio imports: '34'");
    }

    @Test
    void should_reject_when_built_given_payer_outside_fio() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().from("2400222222/0100").build())
                .withMessage("The payer's account is a Fio account with bank code 2010: 2400222222/0100");
    }

    @Test
    void should_reject_when_built_given_due_date_with_five_digit_year() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().date(LocalDate.MAX).build())
                .withMessage("A due date has a four-digit year: " + LocalDate.MAX);
    }
}
