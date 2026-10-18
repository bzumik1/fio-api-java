package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantStatementTest {

    private static final DomesticAccount ACCOUNT = DomesticAccount.parse("2111111111/2010");
    private static final String IBAN = "CZ7920100000002111111111";
    private static final String BIC = "FIOBCZPPXXX";
    private static final Currency CZK = Currency.getInstance("CZK");
    private static final CardTransaction TRANSACTION = new CardTransaction(
            1L, 2L, LocalDate.of(2012, 7, 19), Money.czk("4700.0000"),
            null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

    @Test
    void should_keep_its_own_copy_of_transactions_when_created_given_list_changed_afterwards() {
        List<CardTransaction> transactions = new ArrayList<>(List.of(TRANSACTION));

        MerchantStatement statement = new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, null, null, transactions);
        transactions.clear();

        assertThat(statement.transactions()).containsExactly(TRANSACTION);
        assertThatThrownBy(() -> statement.transactions().add(TRANSACTION)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_keep_period_when_created_given_start_and_end() {
        MerchantStatement statement = new MerchantStatement(
                ACCOUNT, IBAN, BIC, CZK, LocalDate.of(2012, 7, 1), LocalDate.of(2012, 7, 31), List.of());

        assertThat(statement.dateStart()).isEqualTo(LocalDate.of(2012, 7, 1));
        assertThat(statement.dateEnd()).isEqualTo(LocalDate.of(2012, 7, 31));
    }

    @Test
    void should_reject_when_created_given_missing_mandatory_component() {
        List<CardTransaction> none = List.of();

        assertThatNullPointerException().isThrownBy(() -> new MerchantStatement(null, IBAN, BIC, CZK, null, null, none))
                .withMessage("account");
        assertThatNullPointerException().isThrownBy(() -> new MerchantStatement(ACCOUNT, null, BIC, CZK, null, null, none))
                .withMessage("iban");
        assertThatNullPointerException().isThrownBy(() -> new MerchantStatement(ACCOUNT, IBAN, null, CZK, null, null, none))
                .withMessage("bic");
        assertThatNullPointerException().isThrownBy(() -> new MerchantStatement(ACCOUNT, IBAN, BIC, null, null, null, none))
                .withMessage("currency");
        assertThatNullPointerException().isThrownBy(() -> new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, null, null, null));
    }
}
