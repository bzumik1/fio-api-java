package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Currency;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.bzumik1.fio.Fixtures.DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES;
import static io.github.bzumik1.fio.Fixtures.PERIODS_STATEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class FioStatementParserTest {

    private static final String MINIMAL_INFO = """
            {"accountId":"2400222222","currency":"CZK","iban":"CZ7920100000002400222222","bic":"FIOBCZPPXXX",\
            "openingBalance":10.00,"closingBalance":11.00}""";

    private final FioStatementParser parser = new FioStatementParser();

    @Test
    void should_map_info_block_when_parsed_given_periods_statement() {
        FioStatement statement = parser.parse(Fixtures.read(PERIODS_STATEMENT));

        assertThat(statement.info()).isEqualTo(new FioAccountInfo(
                DomesticAccount.parse("2400222222/2010"),
                "CZ7920100000002400222222",
                "FIOBCZPPXXX",
                Currency.getInstance("CZK"),
                Money.czk("195.00"),
                Money.czk("195.01"),
                LocalDate.of(2012, 6, 26),
                LocalDate.of(2012, 6, 30),
                null,
                1148734530L,
                1149190193L,
                1149190192L));
    }

    @Test
    void should_map_every_known_column_when_parsed_given_fully_filled_transaction() {
        FioTransaction transaction = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions().get(0);

        assertThat(transaction)
                .returns(1148734530L, FioTransaction::id)
                .returns(LocalDate.of(2012, 6, 26), FioTransaction::date)
                .returns(Money.czk("1.00"), FioTransaction::amount)
                .returns(new Counterparty(DomesticAccount.parse("2900233333/2010"), "Pavel, Novák", "Fio banka, a.s.", "UNCRITMMXXX"),
                        FioTransaction::counterparty)
                .returns(new PaymentSymbols("1234567890", "0558", "0002"), FioTransaction::symbols)
                .returns("Nákup: PENNY MARKET s.r.o., Jaromer, CZ", FioTransaction::userIdentification)
                .returns(TransactionType.INCOMING_TRANSFER_WITHIN_BANK, FioTransaction::type)
                .returns("Příjem převodem uvnitř banky", FioTransaction::typeLabel)
                .returns("Béďa Trávníček", FioTransaction::executedBy)
                .returns("Libovolný text, který se zobrazí příjemci platby", FioTransaction::messageForRecipient)
                .returns(2105685816L, FioTransaction::instructionId)
                .returns("15.90 EUR", FioTransaction::detail)
                .returns("Hračky pro děti v PENNY MARKET", FioTransaction::comment)
                .returns("2000000003", FioTransaction::payerReference)
                .returns(true, FioTransaction::isIncoming)
                .returns(false, FioTransaction::isOutgoing);
    }

    @Test
    void should_keep_minus_sign_and_scale_when_parsed_given_outgoing_and_small_amounts() {
        List<FioTransaction> transactions = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions();

        assertThat(transactions).extracting(FioTransaction::amount)
                .containsExactly(Money.czk("1.00"), Money.czk("-1.00"), Money.czk("0.01"));
        assertThat(transactions.get(1).isOutgoing()).isTrue();
    }

    @Test
    void should_leave_optional_fields_null_when_parsed_given_null_or_blank_columns() {
        List<FioTransaction> transactions = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions();
        FioTransaction outgoing = transactions.get(1);
        FioTransaction interest = transactions.get(2);

        assertThat(outgoing.counterparty().name()).isNull();
        assertThat(outgoing.counterparty().bic()).isNull();
        assertThat(outgoing.symbols()).isEqualTo(new PaymentSymbols(null, "0558", null));
        assertThat(outgoing.userIdentification()).isNull();
        assertThat(outgoing.comment()).isNull();
        assertThat(interest)
                .returns(Counterparty.none(), FioTransaction::counterparty)
                .returns(PaymentSymbols.none(), FioTransaction::symbols)
                .returns(null, FioTransaction::executedBy)
                .returns(null, FioTransaction::messageForRecipient)
                .returns(null, FioTransaction::detail)
                .returns(null, FioTransaction::payerReference)
                .returns(TransactionType.INTEREST_CREDITED, FioTransaction::type)
                .returns(2107642322L, FioTransaction::instructionId);
    }

    @Test
    void should_keep_raw_columns_when_parsed_given_values_the_typed_fields_normalise() {
        FioTransaction outgoing = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions().get(1);

        assertThat(outgoing.columns())
                .containsEntry(0, new FioColumn(0, "Datum", "2012-06-26+0200"))
                .containsEntry(1, new FioColumn(1, "Objem", "-1.00"))
                .containsEntry(7, new FioColumn(7, "Uživatelská identifikace", " "))
                .containsEntry(22, new FioColumn(22, "ID pohybu", "1148734781"))
                .doesNotContainKeys(5, 6, 10, 16, 18, 26);
        assertThat(List.copyOf(outgoing.columns().keySet())).isSorted();
    }

    @Test
    void should_keep_unknown_column_when_parsed_given_column_the_model_does_not_know() {
        FioTransaction transaction = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions().get(0);

        assertThat(transaction.columns()).containsEntry(30, new FioColumn(30, "Budoucí sloupec", "budoucí hodnota"));
    }

    @Test
    void should_read_dates_in_prague_time_when_parsed_given_documentation_example_with_epoch_milliseconds() {
        FioStatement statement = parser.parse(Fixtures.read(DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES));

        assertThat(statement.info().dateStart()).isEqualTo(LocalDate.of(2012, 6, 26));
        assertThat(statement.info().dateEnd()).isEqualTo(LocalDate.of(2012, 6, 30));
        assertThat(statement.transactions()).extracting(FioTransaction::date)
                .containsExactly(LocalDate.of(2012, 6, 26), LocalDate.of(2012, 6, 26), LocalDate.of(2012, 6, 30));
    }

    @Test
    void should_map_documentation_example_like_the_string_dated_fixture_when_parsed_given_epoch_milliseconds() {
        FioStatement documentationExample = parser.parse(Fixtures.read(DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES));

        assertThat(documentationExample.transactions())
                .extracting(FioTransaction::id, FioTransaction::amount, transaction -> transaction.counterparty().name(),
                        FioTransaction::instructionId)
                .containsExactly(
                        tuple(1148734530L, Money.czk("1.00"), "Pavel, Novák", 2105685816L),
                        tuple(1148734781L, Money.czk("-1.00"), null, 2105687343L),
                        tuple(1149190193L, Money.czk("0.01"), null, 2107642322L));
    }

    @ParameterizedTest
    @CsvSource({
            "2012-07-27+0200, 2012-07-27",
            "2012-07-27+02:00, 2012-07-27",
            "2012-01-15+0100, 2012-01-15",
            "2012-01-15-0500, 2012-01-15",
            "2012-01-15Z, 2012-01-15",
            "2012-01-15, 2012-01-15"
    })
    void should_take_calendar_date_when_parsed_given_date_with_or_without_offset(String raw, LocalDate expected) {
        FioStatement statement = parser.parse(statementWith(MINIMAL_INFO, transaction(replacing(0, "\"" + raw + "\""))));

        assertThat(statement.transactions().get(0).date()).isEqualTo(expected);
    }

    @Test
    void should_leave_optional_info_null_when_parsed_given_last_download_without_movements() {
        String info = """
                {"accountId":"2400222222","bankId":"2010","currency":"CZK","iban":"CZ7920100000002400222222",\
                "bic":"FIOBCZPPXXX","openingBalance":185.05,"closingBalance":185.05,"dateStart":null,"dateEnd":null,\
                "yearList":null,"idList":null,"idFrom":null,"idTo":null,"idLastDownload":1150392361}""";

        FioStatement statement = parser.parse(statementWith(info, null));

        assertThat(statement.transactions()).isEmpty();
        assertThat(statement.info())
                .returns(null, FioAccountInfo::dateStart)
                .returns(null, FioAccountInfo::dateEnd)
                .returns(null, FioAccountInfo::statement)
                .returns(null, FioAccountInfo::firstTransactionId)
                .returns(null, FioAccountInfo::lastTransactionId)
                .returns(1150392361L, FioAccountInfo::bookmarkTransactionId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            ",\"transactionList\":null",
            ",\"transactionList\":{}",
            ",\"transactionList\":{\"transaction\":null}",
            ",\"transactionList\":{\"transaction\":[]}"
    })
    void should_return_no_transactions_when_parsed_given_empty_or_missing_transaction_list(String transactionList) {
        String json = "{\"accountStatement\":{\"info\":" + MINIMAL_INFO + transactionList + "}}";

        assertThat(parser.parse(json).transactions()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "22, ID pohybu",
            "0, Datum",
            "1, Objem",
            "14, Měna",
            "8, Typ"
    })
    void should_reject_naming_the_column_when_parsed_given_transaction_without_mandatory_column(int columnId, String columnName) {
        String json = statementWith(MINIMAL_INFO, transaction(without(columnId)));

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessageContaining("column " + columnId + " (" + columnName + ")");
    }

    @Test
    void should_reject_naming_the_column_when_parsed_given_mandatory_column_with_blank_value() {
        String json = statementWith(MINIMAL_INFO, transaction(replacing(14, "\" \"")));

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessageContaining("column 14 (Měna)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"accountId", "currency", "iban", "bic", "openingBalance", "closingBalance"})
    void should_reject_naming_the_field_when_parsed_given_info_without_mandatory_field(String field) {
        String info = MINIMAL_INFO.replaceFirst("\"" + field + "\":[^,}]+,?", "").replace(",}", "}");
        String json = statementWith(info, transaction(mandatoryColumns()));

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessageContaining("info." + field);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1|\"abc\"|column 1 (Objem)",
            "14|\"XYZ\"|column 14 (Měna)",
            "5|\"12345678901\"|payment symbols",
            "22|\"x1\"|column 22 (ID pohybu)",
            "17|\"x1\"|column 17 (ID pokynu)",
            "0|\"27.07.2012\"|column 0 (Datum)"
    })
    void should_reject_naming_the_column_when_parsed_given_value_of_wrong_type(int columnId, String jsonValue, String expected) {
        String json = statementWith(MINIMAL_INFO, transaction(replacing(columnId, jsonValue)));

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessageContaining(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html><body>Chyba</body></html>", "{}", "[]", "{\"accountStatement\":{}}", ""})
    void should_reject_when_parsed_given_body_that_is_not_an_account_statement(String body) {
        assertThatThrownBy(() -> parser.parse(body)).isInstanceOf(MalformedResponseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\"transactionList\":{\"transaction\":{}}",
            ",\"transactionList\":{\"transaction\":\"none\"}",
            ",\"transactionList\":{\"transaction\":[1]}",
            ",\"transactionList\":{\"transaction\":[null]}"
    })
    void should_reject_when_parsed_given_transaction_list_of_wrong_shape(String transactionList) {
        String json = "{\"accountStatement\":{\"info\":" + MINIMAL_INFO + transactionList + "}}";

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(MalformedResponseException.class);
    }

    @Test
    void should_take_column_id_from_key_when_parsed_given_column_without_usable_id() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add("\"column30\":{\"name\":\"Bez id\",\"value\":\"a\"}");
        columns.add("\"column31\":{\"id\":\"31\",\"name\":\"Textové id\",\"value\":\"b\"}");
        columns.add("\"column32\":{\"id\":99999999999,\"name\":\"Příliš velké id\",\"value\":\"c\"}");
        columns.add("\"column33\":{\"id\":3.5,\"name\":\"Desetinné id\",\"value\":\"d\"}");
        columns.add("\"other\":{\"name\":\"Cizí klíč\",\"value\":\"e\"}");
        columns.add("\"column34\":\"not an object\"");

        FioTransaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction.columns())
                .containsEntry(30, new FioColumn(30, "Bez id", "a"))
                .containsEntry(31, new FioColumn(31, "Textové id", "b"))
                .containsEntry(32, new FioColumn(32, "Příliš velké id", "c"))
                .containsEntry(33, new FioColumn(33, "Desetinné id", "d"))
                .doesNotContainKeys(34)
                .hasSize(mandatoryColumns().size() + 4);
    }

    @Test
    void should_keep_raw_json_text_when_parsed_given_column_values_that_are_not_strings() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add("\"column40\":{\"id\":40,\"name\":\"Pravda\",\"value\":true}");
        columns.add("\"column41\":{\"id\":41,\"name\":\"Objekt\",\"value\":{\"a\":1}}");
        columns.add("\"column42\":{\"id\":42,\"name\":\"Číslo\",\"value\":1.50}");
        columns.add("\"column43\":{\"id\":43,\"value\":\"bez názvu\"}");

        FioTransaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction.columns())
                .containsEntry(40, new FioColumn(40, "Pravda", "true"))
                .containsEntry(41, new FioColumn(41, "Objekt", "{\"a\":1}"))
                .containsEntry(42, new FioColumn(42, "Číslo", "1.50"))
                .containsEntry(43, new FioColumn(43, null, "bez názvu"));
    }

    @Test
    void should_read_statement_number_when_parsed_given_official_statement() {
        String info = MINIMAL_INFO.replace("}", ",\"yearList\":2012,\"idList\":4}");

        FioStatement statement = parser.parse(statementWith(info, null));

        assertThat(statement.info().statement()).isEqualTo(new StatementId(2012, 4));
    }

    @Test
    void should_leave_statement_null_when_parsed_given_only_one_of_year_and_number() {
        String info = MINIMAL_INFO.replace("}", ",\"yearList\":2012,\"idList\":null}");

        assertThat(parser.parse(statementWith(info, null)).info().statement()).isNull();
    }

    @Test
    void should_reject_when_parsed_given_statement_number_out_of_range() {
        String info = MINIMAL_INFO.replace("}", ",\"yearList\":2012,\"idList\":0}");

        assertThatThrownBy(() -> parser.parse(statementWith(info, null)))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessageContaining("statement number");
    }

    @Test
    void should_keep_account_without_bank_code_when_parsed_given_info_without_bank_id() {
        FioStatement statement = parser.parse(statementWith(MINIMAL_INFO, null));

        assertThat(statement.info().account()).isEqualTo(new DomesticAccount(null, "2400222222", null));
    }

    @Test
    void should_reject_naming_the_field_when_parsed_given_account_id_that_is_not_a_number() {
        String info = MINIMAL_INFO.replace("\"2400222222\"", "\"CZ79\"");

        assertThatThrownBy(() -> parser.parse(statementWith(info, null)))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessageContaining("info.accountId");
    }

    @Test
    void should_read_prefixed_account_when_parsed_given_counterparty_with_prefix() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add(column(2, "Protiúčet", "\"19-2000145399\""));
        columns.add(column(3, "Kód banky", "\"0800\""));

        FioTransaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction.counterparty().account()).isEqualTo(DomesticAccount.parse("19-2000145399/0800"));
    }

    @Test
    void should_read_domestic_account_without_bank_code_when_parsed_given_counterparty_without_one() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add(column(2, "Protiúčet", "\"2400222222\""));

        Counterparty counterparty = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0).counterparty();

        assertThat(counterparty.account()).isEqualTo(new DomesticAccount(null, "2400222222", null));
    }

    @Test
    void should_read_foreign_account_when_parsed_given_counterparty_with_iban_or_odd_bank_code() {
        List<String> withIban = new ArrayList<>(mandatoryColumns());
        withIban.add(column(2, "Protiúčet", "\"AT611904300234573201\""));
        withIban.add(column(26, "BIC", "\"ABAGATWWXXX\""));
        List<String> withOddBankCode = new ArrayList<>(mandatoryColumns());
        withOddBankCode.add(column(2, "Protiúčet", "\"12345\""));
        withOddBankCode.add(column(3, "Kód banky", "\"ABCDE\""));

        Counterparty foreign = parser.parse(statementWith(MINIMAL_INFO, transaction(withIban))).transactions().get(0).counterparty();
        Counterparty odd = parser.parse(statementWith(MINIMAL_INFO, transaction(withOddBankCode))).transactions().get(0).counterparty();

        assertThat(foreign.account()).isEqualTo(new ForeignAccount("AT611904300234573201"));
        assertThat(foreign.bic()).isEqualTo("ABAGATWWXXX");
        assertThat(odd.account()).isEqualTo(new ForeignAccount("12345"));
    }

    @Test
    void should_keep_zero_symbol_and_map_unknown_type_when_parsed_given_real_world_values() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add(column(5, "VS", "\"0\""));
        columns.set(4, column(8, "Typ", "\"Něco nového\""));

        FioTransaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction.symbols().variable()).isEqualTo("0");
        assertThat(transaction.type()).isEqualTo(TransactionType.OTHER);
        assertThat(transaction.typeLabel()).isEqualTo("Něco nového");
    }

    private static String statementWith(String info, String transaction) {
        String transactions = transaction == null ? "" : transaction;
        return "{\"accountStatement\":{\"info\":" + info + ",\"transactionList\":{\"transaction\":[" + transactions + "]}}}";
    }

    private static String transaction(List<String> columns) {
        return "{" + String.join(",", columns) + "}";
    }

    private static List<String> mandatoryColumns() {
        return List.of(
                column(22, "ID pohybu", "1148734530"),
                column(0, "Datum", "\"2012-07-27+0200\""),
                column(1, "Objem", "1.00"),
                column(14, "Měna", "\"CZK\""),
                column(8, "Typ", "\"Příjem převodem uvnitř banky\""),
                column(17, "ID pokynu", "2105685816"));
    }

    private static List<String> without(int columnId) {
        return mandatoryColumns().stream()
                .filter(column -> !column.startsWith("\"column" + columnId + "\""))
                .collect(Collectors.toList());
    }

    private static List<String> replacing(int columnId, String jsonValue) {
        List<String> columns = new ArrayList<>(without(columnId));
        String name = mandatoryColumns().stream()
                .filter(column -> column.startsWith("\"column" + columnId + "\""))
                .map(column -> column.replaceFirst(".*\"name\":\"([^\"]*)\".*", "$1"))
                .findFirst()
                .orElse("column" + columnId);
        columns.add(column(columnId, name, jsonValue));
        return columns;
    }

    private static String column(int id, String name, String jsonValue) {
        return "\"column%d\":{\"value\":%s,\"name\":\"%s\",\"id\":%d}".formatted(id, jsonValue, name, id);
    }
}
