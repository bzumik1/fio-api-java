package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

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
                DomesticAccount.parse("2212-2000000699/0300"),
                Money.czk("100.00"),
                new PaymentSymbols("1234567890", "0558", "0000000002"),
                DUE,
                "Hračky pro děti v PENNY MARKET",
                "Dárky & <hračky>",
                "110",
                DomesticPayment.Type.PRIORITY));
        assertThat(payment).isInstanceOf(PaymentOrder.class);
    }

    @Test
    void should_default_to_standard_order_without_symbols_when_built_given_required_parts_only() {
        DomesticPayment payment = PaymentSamples.domestic();

        assertThat(payment.type()).isEqualTo(DomesticPayment.Type.STANDARD);
        assertThat(payment.symbols()).isEqualTo(PaymentSymbols.none());
        assertThat(payment.messageForRecipient()).isNull();
        assertThat(payment.comment()).isNull();
        assertThat(payment.paymentReason()).isNull();
    }

    @Test
    void should_take_accounts_as_objects_when_built() {
        DomesticPayment payment = DomesticPayment.builder()
                .from(DomesticAccount.of("2400222222", "2010"))
                .to(DomesticAccount.of("2000145399", "0800"))
                .amount(Money.czk("1"))
                .date(LocalDate.of(2026, 1, 1))
                .build();

        assertThat(payment.from()).hasToString("2400222222/2010");
        assertThat(payment.to()).hasToString("2000145399/0800");
    }

    @ParameterizedTest
    @CsvSource({"STANDARD, 431001", "PRIORITY, 431005", "DIRECT_DEBIT, 431022"})
    void should_name_fio_code_when_asked_given_type(DomesticPayment.Type type, String code) {
        assertThat(type.code()).isEqualTo(code);
    }

    @Test
    void should_name_missing_part_when_built_given_incomplete_order() {
        assertThatIllegalStateException().isThrownBy(() -> DomesticPayment.builder().build())
                .withMessage("The payer's account is required");
        assertThatIllegalStateException().isThrownBy(() -> DomesticPayment.builder().from("1234562").build())
                .withMessage("The recipient's account is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> DomesticPayment.builder().from("1234562").to("1/0800").build())
                .withMessage("The amount is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> DomesticPayment.builder().from("1234562").to("1/0800").amount(Money.czk("1")).build())
                .withMessage("The date is required");
    }

    @Test
    void should_reject_when_built_given_recipient_without_bank_code() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().to("2000145399").build())
                .withMessage("The recipient's account needs a bank code: 2000145399");
    }

    @Test
    void should_reject_when_built_given_payer_account_outside_fio() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().from("2400222222/0800").build())
                .withMessageStartingWith("The payer's account is a Fio account");
    }

    @Test
    void should_reject_when_built_given_amount_that_cannot_be_paid() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().amount(Money.czk("0")).build());
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().amount(Money.czk("0.001")).build());
    }

    @Test
    void should_reject_when_built_given_text_longer_than_fio_imports() {
        String longest = "x".repeat(140);

        assertThat(domesticBuilder().messageForRecipient(longest).comment(longest).build().comment()).isEqualTo(longest);
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().messageForRecipient(longest + "x").build())
                .withMessage("The message for the recipient is at most 140 characters, not 141");
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().comment(longest + "x").build())
                .withMessage("The comment is at most 140 characters, not 141");
    }

    @Test
    void should_reject_when_built_given_invalid_symbol_or_payment_reason() {
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().variableSymbol("12345678901").build());
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().constantSymbol("05580").build());
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().specificSymbol("x").build());
        assertThatIllegalArgumentException().isThrownBy(() -> domesticBuilder().paymentReason("1100").build())
                .withMessage("The payment reason is 3 digits: '1100'");
    }

    @Test
    void should_drop_blank_texts_when_built() {
        DomesticPayment payment = domesticBuilder().messageForRecipient(" ").comment("").build();

        assertThat(payment.messageForRecipient()).isNull();
        assertThat(payment.comment()).isNull();
    }
}
