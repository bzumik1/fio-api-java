package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static io.github.bzumik1.fio.PaymentSamples.DUE;
import static io.github.bzumik1.fio.PaymentSamples.foreignBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ForeignPaymentTest {

    private static final Beneficiary AMIR = new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK");

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
        ForeignPayment payment = PaymentSamples.foreign();

        assertThat(payment.charges()).isEqualTo(ForeignPayment.Charges.SHARED);
        assertThat(payment.comment()).isNull();
        assertThat(payment.remittanceInfo()).containsExactly("Payment for hotel 032013");
    }

    @Test
    void should_drop_spaces_before_measuring_when_built_given_account_written_in_groups() {
        String account = "AB12" + "3".repeat(30);
        String grouped = account.replaceAll("(.{4})", "$1 ");

        ForeignPayment payment = foreignBuilder().from(DomesticAccount.of("2400222222", "2010")).to(grouped).build();

        assertThat(grouped).hasSizeGreaterThan(34);
        assertThat(payment.to()).isEqualTo(new ForeignAccount(account));
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().to(account + "4").build())
                .withMessage("The recipient's account is at most 34 characters, not 35");
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().to("   ").build())
                .withMessage("A foreign account needs an identifier");
    }

    @ParameterizedTest
    @CsvSource({"PAYER, 470501", "BENEFICIARY, 470502", "SHARED, 470503"})
    void should_name_fio_code_when_asked_given_charges(ForeignPayment.Charges charges, String code) {
        assertThat(charges.code()).isEqualTo(code);
    }

    @Test
    void should_name_missing_part_when_built_given_incomplete_order() {
        assertThatIllegalStateException().isThrownBy(() -> ForeignPayment.builder().build())
                .withMessage("The payer's account is required");
        assertThatIllegalStateException().isThrownBy(() -> ForeignPayment.builder().from("1234562").build())
                .withMessage("The recipient's account is required");
        assertThatIllegalStateException().isThrownBy(() -> ForeignPayment.builder().from("1234562").to("PK36").build())
                .withMessage("The BIC is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> ForeignPayment.builder().from("1234562").to("PK36").bic("ALFHPKKA").build())
                .withMessage("The amount is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> ForeignPayment.builder().from("1234562").to("PK36").bic("ALFHPKKA")
                        .amount(Money.czk("1")).build())
                .withMessage("The date is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> ForeignPayment.builder().from("1234562").to("PK36").bic("ALFHPKKA")
                        .amount(Money.czk("1")).date(DUE).build())
                .withMessage("The beneficiary is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> ForeignPayment.builder().from("1234562").to("PK36").bic("ALFHPKKA")
                        .amount(Money.czk("1")).date(DUE).beneficiary(AMIR).remittanceInfo("x").build())
                .withMessage("The payment reason is required");
    }

    @Test
    void should_reject_when_built_given_beneficiary_without_full_address() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> foreignBuilder().beneficiary(new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", null)).build())
                .withMessage("A payment outside SEPA needs the beneficiary's street, city and country");
    }

    @Test
    void should_need_one_to_four_remittance_lines_when_built() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ForeignPayment.builder().from("1234562").to("PK36").bic("ALFHPKKA")
                        .amount(Money.czk("1")).date(DUE).beneficiary(AMIR).paymentReason("348").build())
                .withMessage("A payment outside SEPA needs at least one remittance line");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> foreignBuilder().remittanceInfo("2").remittanceInfo("3").remittanceInfo("4")
                        .remittanceInfo("5").build())
                .withMessage("At most 4 remittance lines are allowed, not 5");
    }

    @Test
    void should_reject_when_built_given_invalid_bic_amount_comment_or_payment_reason() {
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().bic("12345678").build())
                .withMessage("Not a BIC: '12345678'");
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().amount(Money.czk("-5")).build());
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().comment("x".repeat(141)).build());
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().paymentReason("34").build())
                .withMessage("The payment reason is 3 digits: '34'");
        assertThatIllegalArgumentException().isThrownBy(() -> foreignBuilder().from("2400222222/0100").build());
    }
}
