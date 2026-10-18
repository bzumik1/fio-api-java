package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CardTransactionTest {

    private static final LocalDate DATE = LocalDate.of(2012, 7, 19);
    private static final Money AMOUNT = Money.czk("4700.0000");

    @Test
    void should_leave_optional_components_empty_when_created_given_mandatory_components_only() {
        CardTransaction transaction = mandatoryOnly(DATE, AMOUNT);

        assertThat(transaction.operationId()).isEqualTo(9277937165L);
        assertThat(transaction.orderId()).isEqualTo(8216165940L);
        assertThat(transaction.date()).isEqualTo(DATE);
        assertThat(transaction.amount()).isEqualTo(AMOUNT);
        assertThat(transaction.transactionAmount()).isNull();
        assertThat(transaction.settled()).isNull();
        assertThat(transaction.variableSymbol()).isNull();
    }

    @Test
    void should_reject_when_created_given_no_date() {
        assertThatNullPointerException().isThrownBy(() -> mandatoryOnly(null, AMOUNT)).withMessage("date");
    }

    @Test
    void should_reject_when_created_given_no_amount() {
        assertThatNullPointerException().isThrownBy(() -> mandatoryOnly(DATE, null)).withMessage("amount");
    }

    private static CardTransaction mandatoryOnly(LocalDate date, Money amount) {
        return new CardTransaction(
                9277937165L, 8216165940L, date, amount,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
