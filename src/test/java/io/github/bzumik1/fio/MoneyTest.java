package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void should_be_positive_when_asked_given_amount_above_zero() {
        assertThat(Money.czk("1.00").isPositive()).isTrue();
        assertThat(Money.czk("1.00").isNegative()).isFalse();
    }

    @Test
    void should_be_negative_when_asked_given_amount_below_zero() {
        assertThat(Money.czk("-1.00").isNegative()).isTrue();
        assertThat(Money.czk("-1.00").isPositive()).isFalse();
    }

    @Test
    void should_be_neither_positive_nor_negative_when_asked_given_zero() {
        assertThat(Money.czk(BigDecimal.ZERO).isPositive()).isFalse();
        assertThat(Money.czk(BigDecimal.ZERO).isNegative()).isFalse();
    }

    @Test
    void should_equal_with_same_hash_code_when_compared_given_same_value_at_another_scale() {
        assertThat(Money.czk("1.0")).isEqualTo(Money.czk("1.00")).hasSameHashCodeAs(Money.czk("1.00"));
        assertThat(Money.czk("100")).isEqualTo(Money.czk("100.00")).hasSameHashCodeAs(Money.czk("100.00"));
    }

    @Test
    void should_keep_its_own_scale_when_printed_given_equal_amounts_at_different_scales() {
        assertThat(Money.czk("1.0")).hasToString("1.0 CZK");
        assertThat(Money.czk("1.00")).hasToString("1.00 CZK");
    }

    @Test
    void should_differ_when_compared_given_other_amount() {
        assertThat(Money.czk("1.00")).isNotEqualTo(Money.czk("1.01"));
    }

    @Test
    void should_differ_when_compared_given_same_amount_in_other_currency() {
        assertThat(Money.of("1.00", "CZK")).isNotEqualTo(Money.of("1.00", "EUR"));
    }

    @Test
    void should_differ_when_compared_given_object_that_is_not_money() {
        assertThat(Money.czk("1.00")).isNotEqualTo("1.00 CZK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1E+3", "1e3", "1.5E2", "", "1.", ".5", "+1", "1,00"})
    void should_reject_naming_the_problem_when_created_given_amount_that_is_not_a_plain_decimal(String amount) {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of(amount, "CZK"))
                .withMessage("Not an amount: '" + amount + "'");
    }

    @Test
    void should_reject_naming_the_problem_when_created_given_unknown_currency() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.00", "XYZ")).withMessageContaining("currency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1E+1001", "1E-1001", "1E+999999999"})
    void should_reject_when_created_given_scale_beyond_one_thousand(String amount) {
        BigDecimal huge = new BigDecimal(amount);

        assertThatIllegalArgumentException().isThrownBy(() -> Money.czk(huge)).withMessageContaining("scale within");
        assertThatIllegalArgumentException().isThrownBy(() -> new Money(huge, Currency.getInstance("EUR")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1E+1000", "1E-1000"})
    void should_accept_when_created_given_scale_at_one_thousand(String amount) {
        assertThat(Money.czk(new BigDecimal(amount)).amount()).isEqualByComparingTo(amount);
    }
}
