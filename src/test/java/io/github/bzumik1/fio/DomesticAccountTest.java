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

        assertThat(account).isEqualTo(new DomesticAccount(prefix, number, bankCode));
        assertThat(account.toString()).isEqualTo(printed);
        assertThat(account.numberWithPrefix()).isEqualTo(printed.split("/")[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1234567-1/0800", "12345678901/0800", "123/080", "123/08000", "CZ7920100000002400222222"})
    void should_reject_when_parsed_given_text_that_is_not_an_account_number(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> DomesticAccount.parse(text)).withMessageContaining(text);
    }

    @Test
    void should_reject_when_created_given_component_of_wrong_shape() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DomesticAccount("1234567", "1", "0800"));
        assertThatIllegalArgumentException().isThrownBy(() -> new DomesticAccount(null, "12345678901", "0800"));
        assertThatIllegalArgumentException().isThrownBy(() -> new DomesticAccount(null, "1", "80"));
        assertThatIllegalArgumentException().isThrownBy(() -> new DomesticAccount("1a", "1", "0800"));
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
