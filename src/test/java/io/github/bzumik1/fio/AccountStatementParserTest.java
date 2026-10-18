package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static io.github.bzumik1.fio.Fixtures.DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES;
import static io.github.bzumik1.fio.Fixtures.PERIODS_STATEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class AccountStatementParserTest {

    private static final String MINIMAL_INFO = """
            {"accountId":"2400222222","currency":"CZK","iban":"CZ7920100000002400222222","bic":"FIOBCZPPXXX",\
            "openingBalance":10.00,"closingBalance":11.00}""";

    private final AccountStatementParser parser = new AccountStatementParser();

    @Test
    void should_map_info_block_when_parsed_given_periods_statement() {
        AccountStatement statement = parser.parse(Fixtures.read(PERIODS_STATEMENT));

        assertThat(statement.info()).isEqualTo(new AccountInfo(
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
        Transaction transaction = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions().get(0);

        assertThat(transaction)
                .returns(1148734530L, Transaction::id)
                .returns(LocalDate.of(2012, 6, 26), Transaction::date)
                .returns(Money.czk("1.00"), Transaction::amount)
                .returns(new Counterparty(DomesticAccount.parse("2900233333/2010"), "Pavel, Novák", "Fio banka, a.s.", "UNCRITMMXXX"),
                        Transaction::counterparty)
                .returns(new PaymentSymbols("1234567890", "0558", "0002"), Transaction::symbols)
                .returns("Nákup: PENNY MARKET s.r.o., Jaromer, CZ", Transaction::userIdentification)
                .returns(TransactionType.INCOMING_TRANSFER_WITHIN_BANK, Transaction::type)
                .returns("Příjem převodem uvnitř banky", Transaction::typeLabel)
                .returns("Béďa Trávníček", Transaction::executedBy)
                .returns("Libovolný text, který se zobrazí příjemci platby", Transaction::messageForRecipient)
                .returns(2105685816L, Transaction::instructionId)
                .returns("15.90 EUR", Transaction::detail)
                .returns("Hračky pro děti v PENNY MARKET", Transaction::comment)
                .returns("2000000003", Transaction::payerReference)
                .returns(true, Transaction::isIncoming)
                .returns(false, Transaction::isOutgoing);
    }

    @Test
    void should_keep_minus_sign_and_scale_when_parsed_given_outgoing_and_small_amounts() {
        List<Transaction> transactions = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions();

        assertThat(transactions).extracting(transaction -> transaction.amount().amount().toPlainString())
                .containsExactly("1.00", "-1.00", "0.01");
    }

    @Test
    void should_leave_optional_fields_null_when_parsed_given_outgoing_payment_with_null_or_blank_columns() {
        Transaction outgoing = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions().get(1);

        assertThat(outgoing)
                .returns(new Counterparty(DomesticAccount.parse("2900233333/2010"), null, "Fio banka, a.s.", null),
                        Transaction::counterparty)
                .returns(new PaymentSymbols(null, "0558", null), Transaction::symbols)
                .returns(null, Transaction::userIdentification)
                .returns(null, Transaction::comment)
                .returns(true, Transaction::isOutgoing);
    }

    @Test
    void should_have_no_counterparty_or_symbols_when_parsed_given_credited_interest() {
        Transaction interest = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions().get(2);

        assertThat(interest)
                .returns(Counterparty.none(), Transaction::counterparty)
                .returns(PaymentSymbols.none(), Transaction::symbols)
                .returns(null, Transaction::executedBy)
                .returns(null, Transaction::messageForRecipient)
                .returns(null, Transaction::detail)
                .returns(null, Transaction::payerReference)
                .returns(TransactionType.INTEREST_CREDITED, Transaction::type)
                .returns(2107642322L, Transaction::instructionId);
    }

    @Test
    void should_keep_raw_columns_when_parsed_given_values_the_typed_fields_normalise() {
        Transaction outgoing = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions().get(1);

        assertThat(outgoing.columns())
                .containsEntry(0, new TransactionColumn(0, "Datum", "2012-06-26+0200"))
                .containsEntry(1, new TransactionColumn(1, "Objem", "-1.00"))
                .containsEntry(7, new TransactionColumn(7, "Uživatelská identifikace", " "))
                .containsEntry(22, new TransactionColumn(22, "ID pohybu", "1148734781"))
                .doesNotContainKeys(5, 6, 10, 16, 18, 26);
        assertThat(List.copyOf(outgoing.columns().keySet())).isSorted();
    }

    @Test
    void should_keep_unknown_column_when_parsed_given_column_the_model_does_not_know() {
        Transaction transaction = parser.parse(Fixtures.read(PERIODS_STATEMENT)).transactions().get(0);

        assertThat(transaction.columns()).containsEntry(30, new TransactionColumn(30, "Budoucí sloupec", "budoucí hodnota"));
    }

    @Test
    void should_read_dates_in_prague_time_when_parsed_given_documentation_example_with_epoch_milliseconds() {
        AccountStatement statement = parser.parse(Fixtures.read(DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES));

        assertThat(statement.info().dateStart()).isEqualTo(LocalDate.of(2012, 6, 26));
        assertThat(statement.info().dateEnd()).isEqualTo(LocalDate.of(2012, 6, 30));
        assertThat(statement.transactions()).extracting(Transaction::date)
                .containsExactly(LocalDate.of(2012, 6, 26), LocalDate.of(2012, 6, 26), LocalDate.of(2012, 6, 30));
    }

    @ParameterizedTest
    @CsvSource({
            "1325372400000, 2012-01-01",
            "1340661600000, 2012-06-26",
            "1332630000000, 2012-03-25",
            "1332712800000, 2012-03-26",
            "1351375200000, 2012-10-28",
            "1351465200000, 2012-10-29"
    })
    void should_read_prague_calendar_day_when_parsed_given_epoch_midnight_in_winter_summer_or_on_a_clock_change_day(
            long epochMilliseconds, LocalDate expected) {
        AccountStatement statement = parser.parse(statementWith(MINIMAL_INFO, transaction(replacing(0, Long.toString(epochMilliseconds)))));

        assertThat(statement.transactions().get(0).date()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC", "America/Los_Angeles"})
    @ResourceLock(Resources.TIME_ZONE)
    void should_read_the_same_days_when_parsed_given_another_default_time_zone(String defaultZone) {
        TimeZone original = TimeZone.getDefault();
        AccountStatement statement;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));
            statement = parser.parse(Fixtures.read(DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES));
        } finally {
            TimeZone.setDefault(original);
        }

        assertSoftly(softly -> {
            softly.assertThat(statement.info())
                    .returns(LocalDate.of(2012, 6, 26), AccountInfo::dateStart)
                    .returns(LocalDate.of(2012, 6, 30), AccountInfo::dateEnd);
            softly.assertThat(statement.transactions()).extracting(Transaction::date)
                    .containsExactly(LocalDate.of(2012, 6, 26), LocalDate.of(2012, 6, 26), LocalDate.of(2012, 6, 30));
        });
    }

    @Test
    void should_print_statement_as_2012_slash_3_when_parsed_given_documentation_answer_for_third_statement_of_2012() {
        AccountStatement statement = parser.parse(Fixtures.bytes(Fixtures.STATEMENT_2012_3));

        assertThat(statement.info().statement()).hasToString("2012/3");
    }

    @Test
    void should_read_statement_number_period_and_movement_when_parsed_given_documentation_answer_for_third_statement_of_2012() {
        AccountStatement statement = parser.parse(Fixtures.bytes(Fixtures.STATEMENT_2012_3));

        assertThat(statement.info()).isEqualTo(new AccountInfo(
                DomesticAccount.parse("2400222222/2010"),
                "CZ7920100000002400222222",
                "FIOBCZPPXXX",
                Currency.getInstance("CZK"),
                Money.czk("185.03"),
                Money.czk("185.05"),
                LocalDate.of(2012, 8, 1),
                LocalDate.of(2012, 8, 31),
                new StatementId(2012, 3),
                1155172472L,
                1155172472L,
                null));
        assertThat(statement.transactions())
                .extracting(Transaction::id, Transaction::date, Transaction::amount, Transaction::type, Transaction::instructionId)
                .containsExactly(tuple(1155172472L, LocalDate.of(2012, 8, 31), Money.czk("0.02"),
                        TransactionType.INTEREST_CREDITED, 2135081594L));
    }

    @Test
    void should_read_body_when_parsed_given_utf8_byte_order_mark_before_json() {
        byte[] json = Fixtures.bytes(PERIODS_STATEMENT);
        byte[] withMark = new byte[json.length + 3];
        withMark[0] = (byte) 0xEF;
        withMark[1] = (byte) 0xBB;
        withMark[2] = (byte) 0xBF;
        System.arraycopy(json, 0, withMark, 3, json.length);

        assertThat(parser.parse(withMark)).isEqualTo(parser.parse(json));
    }

    @Test
    void should_read_czech_text_when_parsed_given_utf8_bytes() {
        Transaction transaction = parser.parse(Fixtures.bytes(PERIODS_STATEMENT)).transactions().get(0);

        assertThat(transaction.executedBy()).isEqualTo("Béďa Trávníček");
    }

    @Test
    void should_reject_when_parsed_given_json_nested_deeper_than_the_parser_allows() {
        String nested = "[".repeat(200_000) + "]".repeat(200_000);

        assertThatThrownBy(() -> parser.parse("{\"accountStatement\":" + nested + "}"))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("The body is not valid JSON");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e999999999", "1E-999999999", "1e1001", "1e-1001"})
    void should_reject_without_expanding_it_when_parsed_given_number_with_huge_exponent(String number) {
        String json = statementWith(MINIMAL_INFO, transaction(replacing(1, number)));

        assertThatThrownBy(() -> parser.parse(json))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessageStartingWith("The body holds a number with an exponent no statement needs");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e1000", "1e-1000", "12.5e2"})
    void should_write_number_out_when_parsed_given_exponent_within_limit(String number) {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add(column(42, "Číslo", number));

        Transaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction.columns().get(42).value()).isEqualTo(new BigDecimal(number).toPlainString());
    }

    @ParameterizedTest
    @MethodSource("io.github.bzumik1.fio.DecimalBounds#beyond")
    void should_reject_without_building_the_number_when_parsed_given_amount_as_string_with_too_many_digits(String amount) {
        String json = statementWith(MINIMAL_INFO, transaction(replacing(1, "\"" + amount + "\"")));

        assertThatThrownBy(() -> parser.parse(json))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessageStartingWith("column 1 (Objem) is not a decimal number: '");
    }

    @ParameterizedTest
    @MethodSource("io.github.bzumik1.fio.DecimalBounds#within")
    void should_keep_every_digit_when_parsed_given_amount_as_string_at_the_digit_bounds(String amount) {
        String json = statementWith(MINIMAL_INFO, transaction(replacing(1, "\"" + amount + "\"")));

        assertThat(parser.parse(json).transactions().get(0).amount().amount()).isEqualTo(new BigDecimal(amount));
    }

    @Test
    void should_map_documentation_example_like_the_string_dated_fixture_when_parsed_given_epoch_milliseconds() {
        AccountStatement documentationExample = parser.parse(Fixtures.read(DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES));

        assertThat(documentationExample.transactions())
                .extracting(Transaction::id, Transaction::amount, transaction -> transaction.counterparty().name(),
                        Transaction::instructionId)
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
        AccountStatement statement = parser.parse(statementWith(MINIMAL_INFO, transaction(replacing(0, "\"" + raw + "\""))));

        assertThat(statement.transactions().get(0).date()).isEqualTo(expected);
    }

    @Test
    void should_leave_optional_info_null_when_parsed_given_last_download_without_movements() {
        String info = """
                {"accountId":"2400222222","bankId":"2010","currency":"CZK","iban":"CZ7920100000002400222222",\
                "bic":"FIOBCZPPXXX","openingBalance":185.05,"closingBalance":185.05,"dateStart":null,"dateEnd":null,\
                "yearList":null,"idList":null,"idFrom":null,"idTo":null,"idLastDownload":1150392361}""";

        AccountStatement statement = parser.parse(statementWith(info, null));

        assertThat(statement.transactions()).isEmpty();
        assertThat(statement.info())
                .returns(null, AccountInfo::dateStart)
                .returns(null, AccountInfo::dateEnd)
                .returns(null, AccountInfo::statement)
                .returns(null, AccountInfo::firstTransactionId)
                .returns(null, AccountInfo::lastTransactionId)
                .returns(1150392361L, AccountInfo::bookmarkTransactionId);
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
    @CsvSource(delimiter = '|', value = {
            "<html><body>Chyba</body></html>|The body is not valid JSON",
            "{}|The body has no accountStatement object",
            "[]|The body has no accountStatement object",
            "{\"accountStatement\":{}}|The statement has no info object",
            "''|The body has no accountStatement object"
    })
    void should_reject_when_parsed_given_body_that_is_not_an_account_statement(String body, String reason) {
        assertThatThrownBy(() -> parser.parse(body)).isInstanceOf(MalformedResponseException.class).hasMessage(reason);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            ",\"transactionList\":{\"transaction\":{}}|transactionList.transaction is not an array",
            ",\"transactionList\":{\"transaction\":\"none\"}|transactionList.transaction is not an array",
            ",\"transactionList\":{\"transaction\":[1]}|A transaction is not an object",
            ",\"transactionList\":{\"transaction\":[null]}|A transaction is not an object"
    })
    void should_reject_when_parsed_given_transaction_list_of_wrong_shape(String transactionList, String reason) {
        String json = "{\"accountStatement\":{\"info\":" + MINIMAL_INFO + transactionList + "}}";

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(MalformedResponseException.class).hasMessage(reason);
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

        Transaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction.columns())
                .containsEntry(30, new TransactionColumn(30, "Bez id", "a"))
                .containsEntry(31, new TransactionColumn(31, "Textové id", "b"))
                .containsEntry(32, new TransactionColumn(32, "Příliš velké id", "c"))
                .containsEntry(33, new TransactionColumn(33, "Desetinné id", "d"))
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

        Transaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction.columns())
                .containsEntry(40, new TransactionColumn(40, "Pravda", "true"))
                .containsEntry(41, new TransactionColumn(41, "Objekt", "{\"a\":1}"))
                .containsEntry(42, new TransactionColumn(42, "Číslo", "1.50"))
                .containsEntry(43, new TransactionColumn(43, null, "bez názvu"));
    }

    @Test
    void should_read_statement_number_when_parsed_given_official_statement() {
        String info = MINIMAL_INFO.replace("}", ",\"yearList\":2012,\"idList\":4}");

        AccountStatement statement = parser.parse(statementWith(info, null));

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
        AccountStatement statement = parser.parse(statementWith(MINIMAL_INFO, null));

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

        Transaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

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
    void should_read_foreign_account_and_bic_when_parsed_given_counterparty_with_iban() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add(column(2, "Protiúčet", "\"AT611904300234573201\""));
        columns.add(column(26, "BIC", "\"ABAGATWWXXX\""));

        Counterparty counterparty = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0).counterparty();

        assertThat(counterparty).isEqualTo(new Counterparty(new ForeignAccount("AT611904300234573201"), null, null, "ABAGATWWXXX"));
    }

    @Test
    void should_read_foreign_account_when_parsed_given_counterparty_with_bank_code_that_is_not_four_digits() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add(column(2, "Protiúčet", "\"12345\""));
        columns.add(column(3, "Kód banky", "\"ABCDE\""));

        Counterparty counterparty = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0).counterparty();

        assertThat(counterparty.account()).isEqualTo(new ForeignAccount("12345"));
    }

    @Test
    void should_keep_zero_variable_symbol_when_parsed_given_symbol_left_at_zero() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.add(column(5, "VS", "\"0\""));

        Transaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction.symbols()).isEqualTo(PaymentSymbols.ofVariable("0"));
    }

    @Test
    void should_map_to_other_keeping_the_label_when_parsed_given_type_this_library_does_not_know() {
        List<String> columns = new ArrayList<>(mandatoryColumns());
        columns.set(4, column(8, "Typ", "\"Něco nového\""));

        Transaction transaction = parser.parse(statementWith(MINIMAL_INFO, transaction(columns))).transactions().get(0);

        assertThat(transaction).returns(TransactionType.OTHER, Transaction::type).returns("Něco nového", Transaction::typeLabel);
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
        return String.format(Locale.ROOT, "\"column%d\":{\"value\":%s,\"name\":\"%s\",\"id\":%d}", id, jsonValue, name, id);
    }
}
