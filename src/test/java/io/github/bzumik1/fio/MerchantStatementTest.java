package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantStatementTest {

    private static final DomesticAccount ACCOUNT = DomesticAccount.parse("2111111111/2010");
    private static final String IBAN = "CZ7920100000002111111111";
    private static final String BIC = "FIOBCZPPXXX";
    private static final Currency CZK = Currency.getInstance("CZK");
    private static final CardTransaction TRANSACTION = transaction(1L, Money.czk("4700.0000"));

    @Test
    void should_keep_its_own_copy_of_transactions_when_created_given_list_changed_afterwards() {
        List<CardTransaction> transactions = new ArrayList<>(List.of(TRANSACTION));

        MerchantStatement statement = new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, null, null, transactions);
        transactions.clear();

        assertThat(statement.transactions()).containsExactly(TRANSACTION);
    }

    @Test
    void should_refuse_changes_when_transactions_changed_given_created_statement() {
        MerchantStatement statement = new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, null, null, List.of(TRANSACTION));

        assertThatThrownBy(() -> statement.transactions().add(TRANSACTION)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_keep_period_when_created_given_start_and_end() {
        MerchantStatement statement = new MerchantStatement(
                ACCOUNT, IBAN, BIC, CZK, LocalDate.of(2012, 7, 1), LocalDate.of(2012, 7, 31), List.of());

        assertThat(statement)
                .returns(LocalDate.of(2012, 7, 1), MerchantStatement::dateStart)
                .returns(LocalDate.of(2012, 7, 31), MerchantStatement::dateEnd);
    }

    static Stream<Arguments> missingParts() {
        List<CardTransaction> none = List.of();
        return Stream.of(
                Arguments.of("account", (Supplier<MerchantStatement>) () -> new MerchantStatement(null, IBAN, BIC, CZK, null, null, none)),
                Arguments.of("iban", (Supplier<MerchantStatement>) () -> new MerchantStatement(ACCOUNT, null, BIC, CZK, null, null, none)),
                Arguments.of("bic", (Supplier<MerchantStatement>) () -> new MerchantStatement(ACCOUNT, IBAN, null, CZK, null, null, none)),
                Arguments.of("currency", (Supplier<MerchantStatement>) () -> new MerchantStatement(ACCOUNT, IBAN, BIC, null, null, null, none)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingParts")
    void should_reject_naming_the_part_when_created_given_missing_mandatory_part(String part, Supplier<MerchantStatement> create) {
        assertThatNullPointerException().isThrownBy(create::get).withMessage(part);
    }

    @Test
    void should_reject_when_created_given_no_transaction_list() {
        assertThatNullPointerException().isThrownBy(() -> new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, null, null, null));
    }

    @Test
    void should_reject_when_created_given_missing_transaction_in_the_list() {
        List<CardTransaction> withGap = Arrays.asList(TRANSACTION, null);

        assertThatNullPointerException().isThrownBy(() -> new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, null, null, withGap));
    }

    @Test
    void should_reject_naming_the_transaction_when_created_given_transaction_in_another_currency() {
        List<CardTransaction> transactions = List.of(TRANSACTION, transaction(2L, Money.of("12.50", "EUR")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, null, null, transactions))
                .withMessage("The card transaction 2 is in EUR, not in the account's CZK");
    }

    @Test
    void should_equal_a_statement_with_the_same_values_when_compared_given_amounts_of_another_scale() {
        MerchantStatement first = new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, null, null, List.of(TRANSACTION));
        MerchantStatement second = new MerchantStatement(DomesticAccount.parse("000000-2111111111/2010"), IBAN, BIC, CZK, null, null,
                List.of(transaction(1L, Money.czk("4700"))));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void should_differ_when_compared_given_another_period() {
        MerchantStatement first = new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, LocalDate.of(2012, 7, 1), null, List.of());
        MerchantStatement second = new MerchantStatement(ACCOUNT, IBAN, BIC, CZK, LocalDate.of(2012, 7, 2), null, List.of());

        assertThat(first).isNotEqualTo(second);
    }

    private static CardTransaction transaction(long operationId, Money amount) {
        return new CardTransaction(
                operationId, 2L, LocalDate.of(2012, 7, 19), amount,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
