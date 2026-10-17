package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MoneyTest {

    @Test
    void should_keep_scale_and_currency_when_created_given_textual_amount() {
        Money money = Money.of("100.00", "CZK");

        assertThat(money.amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(money.currency()).isEqualTo(Currency.getInstance("CZK"));
        assertThat(money).hasToString("100.00 CZK");
    }

    @Test
    void should_tell_direction_when_asked_given_sign() {
        assertThat(Money.czk("1.00").isPositive()).isTrue();
        assertThat(Money.czk("1.00").isNegative()).isFalse();
        assertThat(Money.czk("-1.00").isNegative()).isTrue();
        assertThat(Money.czk(BigDecimal.ZERO).isPositive()).isFalse();
        assertThat(Money.czk(BigDecimal.ZERO).isNegative()).isFalse();
    }

    @Test
    void should_reject_naming_the_problem_when_created_given_bad_amount_or_currency() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("abc", "CZK")).withMessageContaining("amount");
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.00", "XYZ")).withMessageContaining("currency");
    }
}
