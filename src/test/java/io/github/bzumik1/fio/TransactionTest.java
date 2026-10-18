package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 5);
    private static final Money AMOUNT = Money.czk("100.00");
    private static final TransactionType TYPE = TransactionType.INCOME;
    private static final String LABEL = "Příjem";
    private static final Counterparty COUNTERPARTY = Counterparty.none();
    private static final PaymentSymbols SYMBOLS = PaymentSymbols.none();
    private static final TransactionColumn ID = new TransactionColumn(22, "ID pohybu", "1147608196");
    private static final TransactionColumn DAY = new TransactionColumn(0, "Datum", "2026-01-05+0100");
    private static final TransactionColumn KIND = new TransactionColumn(8, "Typ", LABEL);

    @Test
    void should_keep_its_own_copy_of_columns_when_created_given_map_changed_afterwards() {
        Map<Integer, TransactionColumn> columns = new LinkedHashMap<>(Map.of(22, ID));

        Transaction transaction = transaction(DATE, AMOUNT, TYPE, LABEL, COUNTERPARTY, SYMBOLS, columns);
        columns.clear();

        assertThat(transaction.columns()).containsExactly(Map.entry(22, ID));
    }

    @Test
    void should_refuse_changes_when_columns_changed_given_created_transaction() {
        Transaction transaction = transaction(DATE, AMOUNT, TYPE, LABEL, COUNTERPARTY, SYMBOLS, Map.of(22, ID));

        assertThatThrownBy(() -> transaction.columns().put(0, DAY)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_order_columns_by_id_when_created_given_columns_in_another_order() {
        Map<Integer, TransactionColumn> columns = new LinkedHashMap<>();
        columns.put(22, ID);
        columns.put(0, DAY);
        columns.put(8, KIND);

        Transaction transaction = transaction(DATE, AMOUNT, TYPE, LABEL, COUNTERPARTY, SYMBOLS, columns);

        assertThat(transaction.columns().keySet()).containsExactly(0, 8, 22);
    }

    @Test
    void should_take_the_columns_as_given_when_created_given_null_value_and_key_other_than_the_column_id() {
        Map<Integer, TransactionColumn> columns = new LinkedHashMap<>();
        columns.put(5, ID);
        columns.put(7, null);

        Transaction transaction = transaction(DATE, AMOUNT, TYPE, LABEL, COUNTERPARTY, SYMBOLS, columns);

        assertThat(transaction.columns()).containsExactly(Map.entry(5, ID), new AbstractMap.SimpleEntry<>(7, null));
    }

    @Test
    void should_reject_when_created_given_column_with_null_key() {
        Map<Integer, TransactionColumn> columns = new LinkedHashMap<>();
        columns.put(null, ID);

        assertThatNullPointerException().isThrownBy(() -> transaction(DATE, AMOUNT, TYPE, LABEL, COUNTERPARTY, SYMBOLS, columns));
    }

    static Stream<Arguments> missingParts() {
        Map<Integer, TransactionColumn> none = Map.of();
        return Stream.of(
                Arguments.of("date", (Supplier<Transaction>) () -> transaction(null, AMOUNT, TYPE, LABEL, COUNTERPARTY, SYMBOLS, none)),
                Arguments.of("amount", (Supplier<Transaction>) () -> transaction(DATE, null, TYPE, LABEL, COUNTERPARTY, SYMBOLS, none)),
                Arguments.of("type", (Supplier<Transaction>) () -> transaction(DATE, AMOUNT, null, LABEL, COUNTERPARTY, SYMBOLS, none)),
                Arguments.of("typeLabel", (Supplier<Transaction>) () -> transaction(DATE, AMOUNT, TYPE, null, COUNTERPARTY, SYMBOLS, none)),
                Arguments.of("counterparty", (Supplier<Transaction>) () -> transaction(DATE, AMOUNT, TYPE, LABEL, null, SYMBOLS, none)),
                Arguments.of("symbols", (Supplier<Transaction>) () -> transaction(DATE, AMOUNT, TYPE, LABEL, COUNTERPARTY, null, none)),
                Arguments.of("columns", (Supplier<Transaction>) () -> transaction(DATE, AMOUNT, TYPE, LABEL, COUNTERPARTY, SYMBOLS, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingParts")
    void should_reject_naming_the_part_when_created_given_missing_mandatory_part(String part, Supplier<Transaction> create) {
        assertThatNullPointerException().isThrownBy(create::get).withMessage(part);
    }

    private static Transaction transaction(LocalDate date, Money amount, TransactionType type, String typeLabel,
                                           Counterparty counterparty, PaymentSymbols symbols, Map<Integer, TransactionColumn> columns) {
        return new Transaction(1147608196L, date, amount, type, typeLabel, counterparty, symbols,
                null, null, null, null, null, null, null, columns);
    }
}
