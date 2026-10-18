package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PaymentTextTest {

    @Test
    void should_strip_or_drop_when_optional_text_checked_given_padded_or_blank_text() {
        assertThat(PaymentText.optional("  Nájem  ", 10, "comment")).isEqualTo("Nájem");
        assertThat(PaymentText.optional("   ", 10, "comment")).isNull();
        assertThat(PaymentText.optional(null, 10, "comment")).isNull();
        assertThat(PaymentText.optional("ab\tc\r\nd", 10, "comment")).isEqualTo("ab\tc\r\nd");
    }

    @Test
    void should_reject_naming_the_field_when_optional_text_checked_given_text_too_long() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.optional("12345678901", 10, "comment"))
                .withMessage("The comment is at most 10 characters, not 11");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\u0000b", "a\u0007b", "a\u000Bb", "a\u001Fb"})
    void should_reject_when_optional_text_checked_given_character_xml_cannot_carry(String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.optional(text, 10, "comment"))
                .withMessage("The comment holds a control character");
    }

    @Test
    void should_keep_text_when_required_text_checked_given_text_within_limit() {
        assertThat(PaymentText.required(" Hans Gruber ", 35, "beneficiary's name")).isEqualTo("Hans Gruber");
    }

    @Test
    void should_reject_when_required_text_checked_given_null_or_blank_text() {
        assertThatNullPointerException().isThrownBy(() -> PaymentText.required(null, 35, "beneficiary's name"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.required(" ", 35, "beneficiary's name"))
                .withMessage("The beneficiary's name is blank");
    }

    @Test
    void should_return_value_or_fail_when_required_part_checked() {
        assertThat(PaymentText.required("x", "date")).isEqualTo("x");
        assertThatIllegalStateException()
                .isThrownBy(() -> PaymentText.required(null, "date"))
                .withMessage("The date is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "100", "100.5", "100.50", "100.500", "1000.000"})
    void should_accept_when_amount_checked_given_positive_amount_with_at_most_two_decimals(String amount) {
        Money money = Money.czk(amount);

        assertThat(PaymentText.positive(money)).isSameAs(money);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1.00"})
    void should_reject_when_amount_checked_given_amount_that_is_not_positive(String amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.positive(Money.czk(amount)))
                .withMessageStartingWith("A payment amount is positive");
    }

    @Test
    void should_reject_when_amount_checked_given_fraction_of_a_cent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.positive(Money.czk(new BigDecimal("1.005"))))
                .withMessage("A payment amount has at most two decimal places: 1.005 CZK");
    }

    @Test
    void should_fail_when_amount_checked_given_no_amount() {
        assertThatIllegalStateException().isThrownBy(() -> PaymentText.positive(null)).withMessage("The amount is required");
    }

    @Test
    void should_check_digits_when_code_checked() {
        assertThat(PaymentText.digits(null, 3, "payment reason")).isNull();
        assertThat(PaymentText.digits("110", 3, "payment reason")).isEqualTo("110");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.digits("11", 3, "payment reason"))
                .withMessage("The payment reason is 3 digits: '11'");
    }

    @ParameterizedTest
    @CsvSource({"ABAGATWWXXX, ABAGATWWXXX", " fiobczpp , FIOBCZPP", "GIBACZPX, GIBACZPX", "DEUTDEFF500, DEUTDEFF500"})
    void should_normalise_when_bic_checked_given_valid_bic(String bic, String expected) {
        assertThat(PaymentText.bic(bic)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABAGATW", "ABAGATWWXX", "ABAGATWWXXXX", "1BAGATWW", "ABAG1TWW", "ABAGAT-W"})
    void should_reject_when_bic_checked_given_text_that_is_not_a_bic(String bic) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.bic(bic))
                .withMessage("Not a BIC: '" + bic + "'");
    }

    @Test
    void should_drop_spaces_and_upper_case_when_iban_checked() {
        assertThat(PaymentText.iban("at61 1904 3002 3457 3201")).isEqualTo("AT611904300234573201");
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentText.iban("AT6X1904")).withMessage("Not an IBAN: 'AT6X1904'");
    }

    @Test
    void should_upper_case_when_country_checked() {
        assertThat(PaymentText.countryCode(" at ")).isEqualTo("AT");
        assertThat(PaymentText.countryCode("TCH")).isEqualTo("TCH");
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentText.countryCode("A")).withMessage("Not a country code: 'A'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2400222222", "2400222222/2010", "7/2010"})
    void should_accept_when_payer_account_checked_given_fio_account(String account) {
        DomesticAccount payer = DomesticAccount.parse(account);

        assertThat(PaymentText.payerAccount(payer)).isSameAs(payer);
    }

    @Test
    void should_reject_when_payer_account_checked_given_account_with_prefix() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.payerAccount(DomesticAccount.parse("19-2400222222/2010")))
                .withMessage("The payer's account is a Fio account, which has no prefix: 19-2400222222/2010");
    }

    @Test
    void should_reject_when_payer_account_checked_given_account_in_another_bank() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.payerAccount(DomesticAccount.parse("2400222222/0800")))
                .withMessage("The payer's account is a Fio account with bank code 2010: 2400222222/0800");
    }

    @Test
    void should_reject_when_payer_account_checked_given_no_account() {
        assertThatNullPointerException().isThrownBy(() -> PaymentText.payerAccount(null)).withMessage("from");
    }
}
