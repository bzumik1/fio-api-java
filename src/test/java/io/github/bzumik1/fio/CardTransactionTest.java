package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CardTransactionTest {

    private static final LocalDate DATE = LocalDate.of(2012, 7, 19);
    private static final Money AMOUNT = Money.czk("4700.0000");

    @Test
    void should_reject_when_created_given_no_date() {
        assertThatNullPointerException().isThrownBy(() -> mandatoryOnly(null, AMOUNT)).withMessage("date");
    }

    @Test
    void should_reject_when_created_given_no_amount() {
        assertThatNullPointerException().isThrownBy(() -> mandatoryOnly(DATE, null)).withMessage("amount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"total fees", "Fio fee", "interchange fee", "card association fee", "flexible commission"})
    void should_reject_naming_the_fee_when_created_given_fee_in_another_currency_than_the_amount(String fee) {
        Money euro = Money.of("1.30", "EUR");

        assertThatIllegalArgumentException().isThrownBy(() -> withFees(
                        fee.equals("total fees") ? euro : null,
                        fee.equals("Fio fee") ? euro : null,
                        fee.equals("interchange fee") ? euro : null,
                        fee.equals("card association fee") ? euro : null,
                        fee.equals("flexible commission") ? euro : null))
                .withMessage("The " + fee + " is in EUR, not in the account's CZK");
    }

    @Test
    void should_keep_fees_when_created_given_every_fee_in_the_currency_of_the_amount() {
        CardTransaction transaction = withFees(Money.czk("3.39"), Money.czk("1.3"), Money.czk("0"), Money.czk("0.25"), Money.czk("1.84"));

        assertThat(transaction)
                .returns(Money.czk("3.39"), CardTransaction::totalFees)
                .returns(Money.czk("1.30"), CardTransaction::fioFee)
                .returns(Money.czk("0.00"), CardTransaction::interchangeFee)
                .returns(Money.czk("0.25"), CardTransaction::cardAssociationFee)
                .returns(Money.czk("1.84"), CardTransaction::flexibleCommission);
    }

    @Test
    void should_accept_charged_amount_in_another_currency_when_created_given_card_charged_abroad() {
        CardTransaction transaction = new CardTransaction(
                1L, 2L, DATE, AMOUNT, null, null, null, null, null, null, null, Money.of("8.10", "EUR"),
                CardTransactionType.FOREIGN, "FOREIGN", null, Money.czk("1"), null, null, null, null, null, null, null);

        assertThat(transaction.transactionAmount()).isEqualTo(Money.of("8.10", "EUR"));
    }

    @Test
    void should_equal_a_transaction_with_the_same_values_when_compared_given_amounts_of_another_scale() {
        CardTransaction first = full(Money.czk("4700.0000"), Money.czk("1.3"));
        CardTransaction second = full(Money.czk("4700"), Money.czk("1.30"));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void should_differ_when_compared_given_another_operation_id() {
        assertThat(mandatoryOnly(DATE, AMOUNT)).isNotEqualTo(new CardTransaction(
                9277937166L, 8216165940L, DATE, AMOUNT,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));
    }

    @Test
    void should_differ_when_compared_given_another_fee() {
        assertThat(full(AMOUNT, Money.czk("1.3"))).isNotEqualTo(full(AMOUNT, Money.czk("1.4")));
    }

    private static CardTransaction full(Money amount, Money fioFee) {
        return new CardTransaction(
                9277937165L, 8216165940L, DATE, amount, "Zaúčtování", "Pobocka 1", 28L, "00203O999999 1",
                OffsetDateTime.parse("2012-07-16T11:28:12+02:00"), "997476", "553937******9052", Money.czk("200.0000"),
                CardTransactionType.ON_US, "ON_US", "MASTERCARD", Money.czk("0"), fioFee, Money.czk("0"), Money.czk("0.25"),
                Money.czk("1.84"), true, DATE, "1234");
    }

    private static CardTransaction withFees(Money total, Money fio, Money interchange, Money association, Money flexible) {
        return new CardTransaction(
                9277937165L, 8216165940L, DATE, AMOUNT,
                null, null, null, null, null, null, null, null, null, null, null,
                total, fio, interchange, association, flexible, null, null, null);
    }

    private static CardTransaction mandatoryOnly(LocalDate date, Money amount) {
        return new CardTransaction(
                9277937165L, 8216165940L, date, amount,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
