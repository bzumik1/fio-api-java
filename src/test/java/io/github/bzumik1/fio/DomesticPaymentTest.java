package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.PaymentSamples.DUE;
import static io.github.bzumik1.fio.PaymentSamples.domesticBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DomesticPaymentTest {

    @Test
    void should_carry_every_part_when_built_given_full_order() {
        DomesticPayment payment = PaymentSamples.fullDomestic();

        assertThat(payment).isEqualTo(new DomesticPayment(
                DomesticAccount.parse("1234562"),
                DomesticAccount.parse("19-2000145399/0800"),
                Money.czk("100.00"),
                new PaymentSymbols("1234567890", "0558", "0000000002"),
                DUE,
                "Hračky pro děti v PENNY MARKET",
                "Dárky & <hračky>",
                "110",
                DomesticPayment.Type.PRIORITY));
    }

    @Test
    void should_default_to_standard_order_without_symbols_when_built_given_required_parts_only() {
        assertThat(PaymentSamples.domestic())
                .returns(DomesticPayment.Type.STANDARD, DomesticPayment::type)
                .returns(PaymentSymbols.none(), DomesticPayment::symbols)
                .returns(null, DomesticPayment::messageForRecipient)
                .returns(null, DomesticPayment::comment)
                .returns(null, DomesticPayment::paymentReason);
    }

    @Test
    void should_take_accounts_as_objects_when_built_given_account_values() {
        DomesticPayment payment = DomesticPayment.builder()
                .from(DomesticAccount.of("2400222222", "2010"))
                .to(DomesticAccount.of("2000145399", "0800"))
                .amount(Money.czk("1"))
                .date(LocalDate.of(2026, 1, 1))
                .build();

        assertThat(payment)
                .returns(DomesticAccount.parse("2400222222/2010"), DomesticPayment::from)
                .returns(DomesticAccount.parse("2000145399/0800"), DomesticPayment::to);
    }

    @ParameterizedTest
    @CsvSource({"STANDARD, 431001", "PRIORITY, 431005", "DIRECT_DEBIT, 431022"})
    void should_name_fio_code_when_asked_given_type(DomesticPayment.Type type, String code) {
        assertThat(type.code()).isEqualTo(code);
    }

    static Stream<Arguments> incompleteOrders() {
        return Stream.of(
                Arguments.of("payer's account", (Supplier<DomesticPayment>) () -> DomesticPayment.builder().build()),
                Arguments.of("recipient's account", (Supplier<DomesticPayment>) () -> DomesticPayment.builder().from("1234562").build()),
                Arguments.of("amount", (Supplier<DomesticPayment>) () -> DomesticPayment.builder().from("1234562")
                        .to("19-2000145399/0800").build()),
                Arguments.of("date", (Supplier<DomesticPayment>) () -> DomesticPayment.builder().from("1234562")
                        .to("19-2000145399/0800").amount(Money.czk("1")).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteOrders")
    void should_name_missing_part_when_built_given_incomplete_order(String part, Supplier<DomesticPayment> build) {
        assertThatIllegalStateException().isThrownBy(build::get).withMessage("The " + part + " is required");
    }

    @Test
    void should_reject_when_built_given_recipient_without_bank_code() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().to("2000145399").build())
                .withMessage("The recipient's account needs a bank code: 2000145399");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2212-2000000699/0300", "2000145398/0800", "18-2000145399/0800", "1/0800"})
    void should_reject_as_mistyped_when_built_given_recipient_failing_the_modulo_11_check(String recipient) {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().to(recipient).build())
                .withMessage("The recipient's account fails the modulo 11 check, so it is mistyped: " + recipient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"19-2000145399/0800", "2000145399/0800", "19/0100", "0/0300"})
    void should_accept_when_built_given_recipient_passing_the_modulo_11_check(String recipient) {
        assertThat(domesticBuilder().to(recipient).build().to()).isEqualTo(DomesticAccount.parse(recipient));
    }

    @Test
    void should_reject_when_built_given_payer_account_outside_fio() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().from("2400222222/0800").build())
                .withMessage("The payer's account is a Fio account with bank code 2010: 2400222222/0800");
    }

    @ParameterizedTest
    @CsvSource({"0, A payment amount is positive: 0 CZK", "0.001, A payment amount has at most two decimal places: 0.001 CZK"})
    void should_reject_when_built_given_amount_that_cannot_be_paid(String amount, String message) {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().amount(Money.czk(amount)).build())
                .withMessage(message);
    }

    @Test
    void should_reject_when_built_given_due_date_with_five_digit_year() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().date(LocalDate.of(10000, 1, 1)).build())
                .withMessage("A due date has a four-digit year: +10000-01-01");
    }

    @Test
    void should_keep_texts_when_built_given_texts_of_exactly_140_characters() {
        String longest = "x".repeat(140);

        assertThat(domesticBuilder().messageForRecipient(longest).comment(longest).build())
                .returns(longest, DomesticPayment::messageForRecipient)
                .returns(longest, DomesticPayment::comment);
    }

    @Test
    void should_reject_when_built_given_message_longer_than_fio_imports() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().messageForRecipient("x".repeat(141)).build())
                .withMessage("The message for the recipient is at most 140 characters, not 141");
    }

    @Test
    void should_reject_when_built_given_comment_longer_than_fio_imports() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().comment("x".repeat(141)).build())
                .withMessage("The comment is at most 140 characters, not 141");
    }

    @ParameterizedTest
    @CsvSource({
            "variable, 12345678901, A variable symbol is up to 10 digits: '12345678901'",
            "constant, 05580, A constant symbol is up to 4 digits: '05580'",
            "specific, x, A specific symbol is up to 10 digits: 'x'"
    })
    void should_reject_naming_the_symbol_when_built_given_symbol_that_is_not_its_digits(String symbol, String value, String message) {
        DomesticPayment.Builder builder = domesticBuilder();
        if (symbol.equals("variable")) {
            builder.variableSymbol(value);
        } else if (symbol.equals("constant")) {
            builder.constantSymbol(value);
        } else {
            builder.specificSymbol(value);
        }

        assertThatIllegalArgumentException().isThrownBy(builder::build).withMessage(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1100", "111", "abc"})
    void should_reject_when_built_given_payment_reason_outside_fio_list(String reason) {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().paymentReason(reason).build())
                .withMessage("Not a payment reason code Fio imports: '" + reason + "'");
    }

    @Test
    void should_drop_blank_texts_when_built_given_blank_message_and_comment() {
        assertThat(domesticBuilder().messageForRecipient(" ").comment("").build())
                .returns(null, DomesticPayment::messageForRecipient)
                .returns(null, DomesticPayment::comment);
    }
}
