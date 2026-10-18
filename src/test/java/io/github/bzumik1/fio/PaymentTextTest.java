package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PaymentTextTest {

    @ParameterizedTest
    @CsvSource(value = {"'  Nájem  '|Nájem", "'ab\tc\r\nd'|'ab\tc\r\nd'", "'emoji 😀'|'emoji 😀'"}, delimiter = '|')
    void should_strip_and_keep_when_optional_text_checked_given_text_xml_carries(String text, String expected) {
        assertThat(PaymentText.optional(text, 10, "comment")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void should_drop_when_optional_text_checked_given_blank_text(String text) {
        assertThat(PaymentText.optional(text, 10, "comment")).isNull();
    }

    @Test
    void should_return_null_when_optional_text_checked_given_no_text() {
        assertThat(PaymentText.optional(null, 10, "comment")).isNull();
    }

    @Test
    void should_reject_naming_the_field_when_optional_text_checked_given_text_too_long() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.optional("12345678901", 10, "comment"))
                .withMessage("The comment is at most 10 characters, not 11");
    }

    @Test
    void should_accept_when_optional_text_checked_given_text_of_exactly_the_limit() {
        assertThat(PaymentText.optional("1234567890", 10, "comment")).isEqualTo("1234567890");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\u0000b", "a\u0007b", "a\u000Bb", "a\u000Cb", "a\u001Fb", "a￾b", "a￿b",
            "a\uD800b", "a\uDBFFb", "a\uDC00b", "a\uDFFFb", "a\uDE00\uD83Db", "a\uD83D"})
    void should_reject_when_optional_text_checked_given_character_xml_cannot_carry(String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.optional(text, 10, "comment"))
                .withMessage("The comment holds a character the import format cannot carry");
    }

    @Test
    void should_keep_text_when_required_text_checked_given_text_within_limit() {
        assertThat(PaymentText.required(" Hans Gruber ", 35, "beneficiary's name")).isEqualTo("Hans Gruber");
    }

    @Test
    void should_reject_when_required_text_checked_given_null_text() {
        assertThatNullPointerException().isThrownBy(() -> PaymentText.required(null, 35, "beneficiary's name"))
                .withMessage("beneficiary's name");
    }

    @Test
    void should_reject_when_required_text_checked_given_blank_text() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.required(" ", 35, "beneficiary's name"))
                .withMessage("The beneficiary's name is blank");
    }

    @Test
    void should_return_value_when_required_part_checked_given_value() {
        assertThat(PaymentText.required("x", "date")).isEqualTo("x");
    }

    @Test
    void should_fail_naming_the_part_when_required_part_checked_given_no_value() {
        assertThatIllegalStateException()
                .isThrownBy(() -> PaymentText.required(null, "date"))
                .withMessage("The date is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "100", "100.5", "100.50", "100.500", "1000.000", "9999999999999999.99",
            "1234567890123456"})
    void should_accept_when_amount_checked_given_positive_amount_with_at_most_sixteen_digits_and_two_decimals(String amount) {
        Money money = Money.czk(amount);

        assertThat(PaymentText.positive(money)).isEqualTo(money);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1.00"})
    void should_reject_when_amount_checked_given_amount_that_is_not_positive(String amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.positive(Money.czk(amount)))
                .withMessage("A payment amount is positive: " + Money.czk(amount));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000000000000000", "10000000000000000.00", "1E+16"})
    void should_reject_when_amount_checked_given_more_than_sixteen_integer_digits(String amount) {
        Money money = Money.czk(new BigDecimal(amount));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.positive(money))
                .withMessage("A payment amount has at most 16 digits before the decimal point: " + money);
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

    static Stream<String> schemaPaymentReasons() {
        return ImportSchema.enumeration("payment_reason_type").stream();
    }

    @ParameterizedTest
    @MethodSource("schemaPaymentReasons")
    void should_accept_when_payment_reason_checked_given_code_of_the_schema(String code) {
        assertThat(PaymentText.paymentReason(code)).isEqualTo(code);
    }

    @Test
    void should_accept_exactly_the_schema_codes_when_payment_reason_checked_given_every_three_digit_code() {
        Set<String> accepted = accepted(
                IntStream.range(0, 1000).mapToObj(code -> String.format("%03d", code)),
                PaymentText::paymentReason);

        assertThat(accepted).isEqualTo(new TreeSet<>(ImportSchema.enumeration("payment_reason_type")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"111", "11", "1100", "abc", " 110", "0110"})
    void should_reject_when_payment_reason_checked_given_code_outside_the_schema(String code) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.paymentReason(code))
                .withMessage("Not a payment reason code Fio imports: '" + code + "'");
    }

    @Test
    void should_return_null_when_payment_reason_checked_given_no_code() {
        assertThat(PaymentText.paymentReason(null)).isNull();
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

    @ParameterizedTest
    @CsvSource({
            "at61 1904 3002 3457 3201, AT611904300234573201",
            "PK36SCBL0000001123456702, PK36SCBL0000001123456702",
            "CZ6508000000192000145399, CZ6508000000192000145399",
            "GB82 WEST 1234 5698 7654 32, GB82WEST12345698765432"
    })
    void should_drop_spaces_and_upper_case_when_iban_checked_given_iban_with_matching_check_digits(String iban, String expected) {
        assertThat(PaymentText.iban(iban)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AT6X1904", "A1611904300234573201", "AT61", "AT61190430023457320!"})
    void should_reject_when_iban_checked_given_text_of_wrong_shape(String iban) {
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentText.iban(iban)).withMessage("Not an IBAN: '" + iban + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AT621904300234573201", "AT611904300234573202", "PK36SCBL0000001123456703", "CZ0008000000192000145399"})
    void should_reject_when_iban_checked_given_check_digits_that_do_not_match(String iban) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.iban(iban))
                .withMessage("Not an IBAN, the check digits do not match: '" + iban + "'");
    }

    static Stream<String> schemaCountries() {
        return ImportSchema.enumeration("country_type").stream();
    }

    @ParameterizedTest
    @MethodSource("schemaCountries")
    void should_accept_in_any_case_when_country_checked_given_code_of_the_schema(String code) {
        assertThat(PaymentText.countryCode(" " + code.toLowerCase(Locale.ROOT) + " ")).isEqualTo(code);
    }

    @Test
    void should_accept_exactly_the_schema_codes_when_country_checked_given_every_two_letter_code() {
        Stream<String> twoLetterCodes = IntStream.range(0, 26 * 26)
                .mapToObj(code -> "" + (char) ('A' + code / 26) + (char) ('A' + code % 26));

        assertThat(accepted(twoLetterCodes, PaymentText::countryCode))
                .isEqualTo(new TreeSet<>(ImportSchema.enumeration("country_type")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TCH", "CZE", "A", "UK", "EU", "Pakistan", ""})
    void should_reject_when_country_checked_given_code_outside_the_schema(String code) {
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentText.countryCode(code))
                .withMessage("Not a country code Fio imports: '" + code + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0001-01-01", "2013-04-25", "9999-12-31"})
    void should_accept_when_due_date_checked_given_date_with_four_digit_year(LocalDate date) {
        assertThat(PaymentText.dueDate(date)).isEqualTo(date);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000-12-31", "-0001-01-01", "+10000-01-01"})
    void should_reject_when_due_date_checked_given_year_the_schema_date_cannot_carry(String date) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentText.dueDate(LocalDate.parse(date)))
                .withMessage("A due date has a four-digit year: " + date);
    }

    @Test
    void should_reject_when_due_date_checked_given_no_date() {
        assertThatNullPointerException().isThrownBy(() -> PaymentText.dueDate(null)).withMessage("date");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2400222222", "2400222222/2010", "7/2010"})
    void should_accept_when_payer_account_checked_given_fio_account(String account) {
        DomesticAccount payer = DomesticAccount.parse(account);

        assertThat(PaymentText.payerAccount(payer)).isEqualTo(payer);
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

    private static Set<String> accepted(Stream<String> candidates, UnaryOperator<String> check) {
        Set<String> accepted = new TreeSet<>();
        candidates.forEach(candidate -> {
            try {
                assertThat(check.apply(candidate)).isEqualTo(candidate);
                accepted.add(candidate);
            } catch (IllegalArgumentException refused) {
                assertThat(refused).hasMessageContaining(candidate);
            }
        });
        return accepted;
    }
}
