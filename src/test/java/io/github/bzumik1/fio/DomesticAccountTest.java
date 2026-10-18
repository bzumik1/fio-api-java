package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DomesticAccountTest {

    @ParameterizedTest
    @CsvSource(value = {
            "19-2000145399/0800 | 19 | 2000145399 | 0800 | 19-2000145399/0800",
            "2000145399/0800 | | 2000145399 | 0800 | 2000145399/0800",
            "2400222222 | | 2400222222 | | 2400222222",
            "000019-2000145399/0800 | 19 | 2000145399 | 0800 | 19-2000145399/0800",
            "000000-0000000123/2010 | | 123 | 2010 | 123/2010",
            " 7/2010 | | 7 | 2010 | 7/2010"
    }, delimiter = '|')
    void should_read_and_normalise_when_parsed_given_written_account_number(
            String text, String prefix, String number, String bankCode, String printed) {
        DomesticAccount account = DomesticAccount.parse(text);

        assertThat(account)
                .isEqualTo(new DomesticAccount(prefix, number, bankCode))
                .hasToString(printed)
                .returns(printed.split("/")[0], DomesticAccount::numberWithPrefix);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1234567-1/0800", "12345678901/0800", "123/080", "123/08000", "CZ7920100000002400222222"})
    void should_reject_when_parsed_given_text_that_is_not_an_account_number(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> DomesticAccount.parse(text)).withMessageContaining(text);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1234567 | 1           | 0800 | An account prefix is up to 6 digits: '1234567'",
            "1a      | 1           | 0800 | An account prefix is up to 6 digits: '1a'",
            "        | 12345678901 | 0800 | An account number is up to 10 digits: '12345678901'",
            "        | 1           | 80   | A bank code is 4 digits: '80'"
    }, delimiter = '|')
    void should_reject_naming_the_part_when_created_given_component_of_wrong_shape(
            String prefix, String number, String bankCode, String message) {
        assertThatIllegalArgumentException().isThrownBy(() -> new DomesticAccount(prefix, number, bankCode)).withMessage(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"19-2000145399/0800", "2000145399", "0/0100", "19/0100", "1234567899/0100", "9999999999/0300",
            "107000000/0100", "100001-19/0300", "35-1234562/0100", "000019-2000145399/0800"})
    void should_pass_when_checksum_checked_given_prefix_and_number_divisible_by_eleven(String account) {
        assertThat(DomesticAccount.parse(account).hasValidChecksum()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"18-2000145399/0800", "2212-2000000699/0300", "2000145398/0800", "19-2000145398/0800", "1/0800",
            "2400222222/2010", "100000-19/0300"})
    void should_fail_when_checksum_checked_given_prefix_or_number_not_divisible_by_eleven(String account) {
        assertThat(DomesticAccount.parse(account).hasValidChecksum()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "19-2000145399/0800, CZ6508000000192000145399",
            "2400222222/2010, CZ0520100000002400222222",
            "1234562/2010, CZ7820100000000001234562"
    })
    void should_compute_iban_when_asked_given_account_with_bank_code(String account, String iban) {
        assertThat(DomesticAccount.parse(account).toIban()).isEqualTo(iban);
    }

    @Test
    void should_refuse_iban_when_asked_given_account_without_bank_code() {
        assertThatIllegalStateException().isThrownBy(() -> DomesticAccount.parse("2400222222").toIban());
    }

    @Test
    void should_create_without_prefix_when_created_given_number_and_bank_code() {
        assertThat(DomesticAccount.of("2400222222", "2010")).isEqualTo(DomesticAccount.parse("2400222222/2010"));
    }
}
