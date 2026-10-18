package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountStatementTest {

    private static final AccountInfo INFO = new AccountInfo(
            DomesticAccount.parse("2400222222/2010"), "CZ7920100000002400222222", "FIOBCZPPXXX", Currency.getInstance("CZK"),
            Money.czk("10.00"), Money.czk("110.00"), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
            null, 1147608196L, 1147608196L, null);
    private static final Transaction TRANSACTION = new Transaction(
            1147608196L, LocalDate.of(2026, 1, 5), Money.czk("100.00"), TransactionType.INCOME, "Příjem",
            Counterparty.none(), PaymentSymbols.none(), null, null, null, null, null, null, null, Map.of());

    @Test
    void should_keep_its_own_copy_of_transactions_when_created_given_list_changed_afterwards() {
        List<Transaction> transactions = new ArrayList<>(List.of(TRANSACTION));

        AccountStatement statement = new AccountStatement(INFO, transactions);
        transactions.clear();

        assertThat(statement.transactions()).containsExactly(TRANSACTION);
    }

    @Test
    void should_refuse_changes_when_transactions_changed_given_created_statement() {
        AccountStatement statement = new AccountStatement(INFO, List.of(TRANSACTION));

        assertThatThrownBy(() -> statement.transactions().add(TRANSACTION)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_reject_naming_the_info_when_created_given_no_info() {
        assertThatNullPointerException().isThrownBy(() -> new AccountStatement(null, List.of())).withMessage("info");
    }

    @Test
    void should_reject_when_created_given_no_transaction_list() {
        assertThatNullPointerException().isThrownBy(() -> new AccountStatement(INFO, null));
    }

    @Test
    void should_reject_when_created_given_missing_transaction_in_the_list() {
        List<Transaction> withGap = Arrays.asList(TRANSACTION, null);

        assertThatNullPointerException().isThrownBy(() -> new AccountStatement(INFO, withGap));
    }
}
