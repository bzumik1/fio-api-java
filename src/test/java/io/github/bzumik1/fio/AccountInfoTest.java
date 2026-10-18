package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AccountInfoTest {

    private static final DomesticAccount ACCOUNT = DomesticAccount.parse("2400222222/2010");
    private static final Currency CZK = Currency.getInstance("CZK");

    @Test
    void should_keep_balances_when_created_given_balances_in_the_account_currency() {
        AccountInfo info = info(Money.czk("185.03"), Money.czk("185.05"));

        assertThat(info)
                .returns(Money.czk("185.03"), AccountInfo::openingBalance)
                .returns(Money.czk("185.05"), AccountInfo::closingBalance);
    }

    @ParameterizedTest
    @CsvSource({"EUR, CZK, EUR, CZK", "CZK, EUR, CZK, EUR", "EUR, EUR, EUR, EUR"})
    void should_reject_naming_the_currencies_when_created_given_balance_in_another_currency(
            String opening, String closing, String openingName, String closingName) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> info(Money.of("1.00", opening), Money.of("2.00", closing)))
                .withMessage("The balances are in " + openingName + " and " + closingName + ", not in the account's CZK");
    }

    @Test
    void should_reject_when_created_given_no_opening_balance() {
        assertThatNullPointerException().isThrownBy(() -> info(null, Money.czk("1"))).withMessage("openingBalance");
    }

    @Test
    void should_reject_when_created_given_no_closing_balance() {
        assertThatNullPointerException().isThrownBy(() -> info(Money.czk("1"), null)).withMessage("closingBalance");
    }

    @Test
    void should_equal_info_with_the_same_values_when_compared_given_balances_of_another_scale() {
        assertThat(info(Money.czk("185.0"), Money.czk("185"))).isEqualTo(info(Money.czk("185.00"), Money.czk("185.000")));
    }

    private static AccountInfo info(Money opening, Money closing) {
        return new AccountInfo(ACCOUNT, "CZ7920100000002400222222", "FIOBCZPPXXX", CZK, opening, closing,
                null, null, new StatementId(2012, 3), null, null, null);
    }
}
