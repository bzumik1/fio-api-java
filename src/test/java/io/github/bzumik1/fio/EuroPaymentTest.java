package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static io.github.bzumik1.fio.PaymentSamples.DUE;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.PaymentSamples.euroBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EuroPaymentTest {

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
        EuroPayment payment = PaymentSamples.euro();

        assertThat(payment.type()).isEqualTo(EuroPayment.Type.STANDARD);
        assertThat(payment.bic()).isNull();
        assertThat(payment.comment()).isNull();
        assertThat(payment.remittanceInfo()).isEmpty();
        assertThat(payment.paymentReason()).isNull();
        assertThat(payment.beneficiary()).isEqualTo(Beneficiary.named("Hans Gruber"));
        assertThat(payment.symbols()).isEqualTo(PaymentSymbols.none());
    }

    @Test
    void should_normalise_iban_and_bic_when_built_given_written_forms() {
        EuroPayment payment = euroBuilder()
                .from(DomesticAccount.of("2400222222", "2010"))
                .to("at61 1904 3002 3457 3201")
                .bic(" abagatww ")
                .build();

        assertThat(payment.to()).isEqualTo(new ForeignAccount("AT611904300234573201"));
        assertThat(payment.bic()).isEqualTo("ABAGATWW");
    }

    @ParameterizedTest
    @CsvSource({"STANDARD, 431008", "PRIORITY, 431009", "INSTANT, 431018"})
    void should_name_fio_code_when_asked_given_type(EuroPayment.Type type, String code) {
        assertThat(type.code()).isEqualTo(code);
    }

    @Test
    void should_name_missing_part_when_built_given_incomplete_order() {
        assertThatIllegalStateException().isThrownBy(() -> EuroPayment.builder().build())
                .withMessage("The payer's account is required");
        assertThatIllegalStateException().isThrownBy(() -> EuroPayment.builder().from("1234562").build())
                .withMessage("The recipient's account is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> EuroPayment.builder().from("1234562").to("AT611904300234573201").build())
                .withMessage("The amount is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> EuroPayment.builder().from("1234562").to("AT611904300234573201").amount(euro("1")).build())
                .withMessage("The date is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> EuroPayment.builder().from("1234562").to("AT611904300234573201").amount(euro("1"))
                        .date(DUE).build())
                .withMessage("The beneficiary is required");
    }

    @Test
    void should_reject_when_built_given_amount_not_in_euro() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().amount(Money.czk("100")).build())
                .withMessage("A SEPA payment is in euro, not CZK");
    }

    @Test
    void should_reject_when_built_given_malformed_iban_bic_or_payer() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().to("2000145399/0800").build());
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().bic("ABAG").build());
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().from("19-2400222222").build());
    }

    @Test
    void should_accept_three_remittance_lines_of_35_characters_and_no_more_when_built() {
        String longest = "x".repeat(35);

        assertThat(euroBuilder().remittanceInfo(longest).remittanceInfo(longest).remittanceInfo(longest).build()
                .remittanceInfo()).hasSize(3);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> euroBuilder().remittanceInfo("1").remittanceInfo("2").remittanceInfo("3")
                        .remittanceInfo("4").build())
                .withMessage("At most 3 remittance lines are allowed, not 4");
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().remittanceInfo(longest + "x").build())
                .withMessage("The remittance line is at most 35 characters, not 36");
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
    void should_reject_when_built_given_comment_or_payment_reason_fio_does_not_import() {
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().comment("x".repeat(141)).build())
                .withMessage("The comment is at most 140 characters, not 141");
        assertThatIllegalArgumentException().isThrownBy(() -> euroBuilder().paymentReason("abc").build())
                .withMessage("The payment reason is 3 digits: 'abc'");
    }
}
