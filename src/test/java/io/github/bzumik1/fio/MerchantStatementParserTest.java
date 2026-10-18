package io.github.bzumik1.fio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.github.bzumik1.fio.Fixtures.MERCHANT_DOCUMENTATION_EXAMPLE;
import static io.github.bzumik1.fio.Fixtures.MERCHANT_SCHEMA_EXAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantStatementParserTest {

    private static final Map<String, String> MINIMAL_INFO = ordered(
            "accountId", "2111111111",
            "bankId", "2010",
            "currency", "CZK",
            "iban", "CZ7920100000002111111111",
            "bic", "FIOBCZPPXXX");
    private static final Map<String, String> MINIMAL_TRANSACTION = ordered(
            "operationId", "1",
            "orderId", "2",
            "date", "2026-01-05",
            "amount", "10.00");

    private final MerchantStatementParser parser = new MerchantStatementParser();

    private StubFioServer server;

    @BeforeEach
    void startServer() {
        server = StubFioServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void should_map_info_and_every_field_when_parsed_given_documentation_example() {
        MerchantStatement statement = parser.parse(Fixtures.read(MERCHANT_DOCUMENTATION_EXAMPLE));

        assertThat(statement).isEqualTo(new MerchantStatement(
                DomesticAccount.parse("2111111111/2010"),
                "CZ7920100000002111111111",
                "FIOBCZPPXXX",
                Currency.getInstance("CZK"),
                LocalDate.of(2012, 7, 1),
                LocalDate.of(2012, 7, 31),
                List.of(new CardTransaction(
                        9277937165L,
                        8216165940L,
                        LocalDate.of(2012, 7, 19),
                        Money.czk("4700.0000"),
                        "Zaúčtování POS terminálů MasterCard, Operace ON-US",
                        "Pobocka 1",
                        28L,
                        "00203O999999 1",
                        OffsetDateTime.parse("2012-07-16T11:28:12+02:00"),
                        "997476",
                        "553937******9052",
                        Money.czk("200.0000"),
                        CardTransactionType.ON_US,
                        "ON_US",
                        "MASTERCARD",
                        Money.czk("0.0000"),
                        Money.czk("1.3"),
                        Money.czk("0.0000"),
                        Money.czk("0.25"),
                        Money.czk("1.84"),
                        true,
                        LocalDate.of(2012, 7, 19),
                        null))));
    }

    @Test
    void should_read_namespaced_schema_spelling_when_parsed_given_schema_example() {
        MerchantStatement statement = parser.parse(Fixtures.read(MERCHANT_SCHEMA_EXAMPLE));

        assertThat(statement.account()).isEqualTo(DomesticAccount.parse("19-2000145399/2010"));
        assertThat(statement.currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(statement.dateStart()).isNull();
        assertThat(statement.dateEnd()).isNull();
        assertThat(statement.transactions()).hasSize(2);
        assertThat(statement.transactions().get(0))
                .returns(8216165941L, CardTransaction::operationId)
                .returns(Money.of("-12.50", "EUR"), CardTransaction::amount)
                .returns(OffsetDateTime.parse("2026-01-04T23:59:59.250Z"), CardTransaction::transactionDateTime)
                .returns(Money.of("-12.50", "EUR"), CardTransaction::transactionAmount)
                .returns(CardTransactionType.FOREIGN, CardTransaction::type)
                .returns("FOREIGN", CardTransaction::typeLabel)
                .returns("VISA", CardTransaction::cardIssuer)
                .returns(Money.of("0.10", "EUR"), CardTransaction::cardAssociationFee)
                .returns(false, CardTransaction::settled)
                .returns("12345678911234567891", CardTransaction::variableSymbol);
    }

    @Test
    void should_leave_every_optional_field_empty_when_parsed_given_transaction_with_mandatory_fields_only() {
        CardTransaction transaction = parser.parse(Fixtures.read(MERCHANT_SCHEMA_EXAMPLE)).transactions().get(1);

        assertThat(transaction).isEqualTo(new CardTransaction(
                8216165942L, 9277937167L, LocalDate.of(2026, 1, 6), Money.of("0", "EUR"),
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));
    }

    @Test
    void should_read_documented_variable_symbol_spelling_when_parsed_given_upper_case_vs() {
        CardTransaction transaction = only(transaction("VS", "12345678911234567891"));

        assertThat(transaction.variableSymbol()).isEqualTo("12345678911234567891");
    }

    @Test
    void should_treat_blank_elements_as_absent_when_parsed_given_empty_optional_fields() {
        CardTransaction transaction = only(transaction(
                "note", "   ", "branchName", "", "transactionId", "", "settlement", " "));

        assertThat(transaction.note()).isNull();
        assertThat(transaction.branchName()).isNull();
        assertThat(transaction.transactionId()).isNull();
        assertThat(transaction.settled()).isNull();
    }

    @Test
    void should_strip_surrounding_whitespace_when_parsed_given_padded_values() {
        CardTransaction transaction = only(transaction("transactionId", "\n  28 ", "deviceId", " 00203O999999 1 "));

        assertThat(transaction.transactionId()).isEqualTo(28L);
        assertThat(transaction.deviceId()).isEqualTo("00203O999999 1");
    }

    @Test
    void should_join_text_around_comments_and_cdata_when_parsed_given_mixed_content() {
        String body = "<!-- answer --><MerchantStatement>" + info(MINIMAL_INFO)
                + "<TransactionList><Transaction>" + elements(MINIMAL_TRANSACTION)
                + "<note>Platba <!-- x --><![CDATA[<kartou> & hotově]]><?pi data?></note>"
                + "</Transaction></TransactionList></MerchantStatement>";

        assertThat(parser.parse(body).transactions()).extracting(CardTransaction::note)
                .containsExactly("Platba <kartou> & hotově");
    }

    @Test
    void should_ignore_unknown_elements_when_parsed_given_fields_this_client_does_not_know() {
        String body = "<MerchantStatement><Unknown>1</Unknown>" + info(MINIMAL_INFO)
                + "<TransactionList><Summary>3</Summary><Transaction>" + elements(MINIMAL_TRANSACTION)
                + "<newField><nested>x</nested></newField></Transaction></TransactionList></MerchantStatement>";

        assertThat(parser.parse(body).transactions()).extracting(CardTransaction::operationId).containsExactly(1L);
    }

    @Test
    void should_return_no_transactions_when_parsed_given_empty_transaction_list() {
        MerchantStatement statement = parser.parse(statement(""));

        assertThat(statement.transactions()).isEmpty();
        assertThat(statement.account()).isEqualTo(DomesticAccount.parse("2111111111/2010"));
    }

    @Test
    void should_return_no_transactions_when_parsed_given_statement_without_transaction_list() {
        MerchantStatement statement = parser.parse("<MerchantStatement>" + info(MINIMAL_INFO) + "</MerchantStatement>");

        assertThat(statement.transactions()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "2012-07-19+02:00", "2012-07-19+0200", "2012-07-19Z", "2012-07-19"
    })
    void should_read_calendar_day_when_parsed_given_date_with_or_without_offset(String date) {
        CardTransaction transaction = only(transaction("date", date, "settlementDate", date));

        assertThat(transaction.date()).isEqualTo(LocalDate.of(2012, 7, 19));
        assertThat(transaction.settlementDate()).isEqualTo(LocalDate.of(2012, 7, 19));
    }

    @ParameterizedTest
    @CsvSource({
            "2012-07-16T11:28:12, 2012-07-16T11:28:12+02:00",
            "2026-01-04T10:00, 2026-01-04T10:00+01:00"
    })
    void should_take_prague_offset_when_parsed_given_transaction_time_without_offset(String time, String expected) {
        CardTransaction transaction = only(transaction("transactionDateTime", time));

        assertThat(transaction.transactionDateTime()).isEqualTo(OffsetDateTime.parse(expected));
    }

    @ParameterizedTest
    @CsvSource({"true, true", "1, true", "false, false", "0, false"})
    void should_read_schema_boolean_when_parsed_given_settlement_flag(String flag, boolean expected) {
        assertThat(only(transaction("settlement", flag)).settled()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "operationId, x1, a whole number",
            "orderId, 1.5, a whole number",
            "transactionId, abc, a whole number",
            "date, 19.07.2012, a date",
            "settlementDate, 2012-13-01, a date",
            "amount, '4 700', a decimal number",
            "totalFees, '20,00', a decimal number",
            "fioFee, five, a decimal number",
            "interchangeFee, 5.0.0, a decimal number",
            "cardAssociationFee, -, a decimal number",
            "cardAsosiationFee, 1e, a decimal number",
            "flexibleCommission, ?, a decimal number",
            "transactionDateTime, 2012-07-16, a date and time",
            "settlement, yes, true or false"
    })
    void should_reject_naming_the_element_when_parsed_given_transaction_field_of_wrong_type(
            String element, String value, String expected) {
        assertThatThrownBy(() -> parser.parse(statement(transaction(element, value))))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Transaction[1]/" + element + " is not " + expected + ": '" + value + "'");
    }

    @Test
    void should_reject_naming_the_element_when_parsed_given_transaction_amount_in_unknown_currency() {
        String transaction = transaction("transactionAmount", "200.00", "transactionCurrency", "KC");

        assertThatThrownBy(() -> parser.parse(statement(transaction)))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Transaction[1]/transactionCurrency is not an ISO 4217 currency code: 'KC'");
    }

    @Test
    void should_reject_when_parsed_given_transaction_amount_without_currency() {
        assertThatThrownBy(() -> parser.parse(statement(transaction("transactionAmount", "200.00"))))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Transaction[1]/transactionCurrency is missing");
    }

    @Test
    void should_ignore_currency_when_parsed_given_transaction_currency_without_amount() {
        assertThat(only(transaction("transactionCurrency", "CZK")).transactionAmount()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"operationId", "orderId", "date", "amount"})
    void should_reject_naming_the_element_when_parsed_given_transaction_without_mandatory_field(String element) {
        Map<String, String> fields = new LinkedHashMap<>(MINIMAL_TRANSACTION);
        fields.remove(element);

        assertThatThrownBy(() -> parser.parse(statement("<Transaction>" + elements(fields) + "</Transaction>")))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Transaction[1]/" + element + " is missing");
    }

    @Test
    void should_count_transactions_from_one_when_rejected_given_second_transaction_of_wrong_type() {
        String transactions = transaction("operationId", "1") + transaction("operationId", "two");

        assertThatThrownBy(() -> parser.parse(statement(transactions)))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessageStartingWith("Transaction[2]/operationId is not a whole number");
    }

    @ParameterizedTest
    @ValueSource(strings = {"accountId", "bankId", "currency", "iban", "bic"})
    void should_reject_naming_the_element_when_parsed_given_info_without_mandatory_field(String element) {
        Map<String, String> fields = new LinkedHashMap<>(MINIMAL_INFO);
        fields.remove(element);

        assertThatThrownBy(() -> parser.parse(statement(fields, "")))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Info/" + element + " is missing");
    }

    @ParameterizedTest
    @CsvSource({
            "currency, KC, an ISO 4217 currency code",
            "dateStart, 1.7.2012, a date",
            "dateEnd, yesterday, a date"
    })
    void should_reject_naming_the_element_when_parsed_given_info_field_of_wrong_type(
            String element, String value, String expected) {
        Map<String, String> fields = new LinkedHashMap<>(MINIMAL_INFO);
        fields.put(element, value);

        assertThatThrownBy(() -> parser.parse(statement(fields, "")))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Info/" + element + " is not " + expected + ": '" + value + "'");
    }

    @ParameterizedTest
    @CsvSource({"accountId, 12-34-56", "bankId, 20100"})
    void should_reject_naming_both_elements_when_parsed_given_info_that_is_not_an_account_number(String element, String value) {
        Map<String, String> fields = new LinkedHashMap<>(MINIMAL_INFO);
        fields.put(element, value);

        assertThatThrownBy(() -> parser.parse(statement(fields, "")))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Info/accountId and Info/bankId are not an account number: '"
                        + fields.get("accountId") + "/" + fields.get("bankId") + "'")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not xml", "{\"accountStatement\":{}}", "<MerchantStatement>", "<a></b>"})
    void should_reject_when_parsed_given_body_that_is_not_well_formed_xml(String body) {
        assertThatThrownBy(() -> parser.parse(body))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("The body is not well-formed XML");
    }

    @Test
    void should_reject_naming_the_element_when_parsed_given_other_root_element() {
        assertThatThrownBy(() -> parser.parse("<AccountStatement><Info/></AccountStatement>"))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("The root element is AccountStatement, not MerchantStatement");
    }

    @Test
    void should_reject_when_parsed_given_statement_without_info() {
        assertThatThrownBy(() -> parser.parse("<MerchantStatement><TransactionList/></MerchantStatement>"))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("MerchantStatement/Info is missing");
    }

    @Test
    void should_reject_when_parsed_given_statement_with_two_info_elements() {
        String body = "<MerchantStatement>" + info(MINIMAL_INFO) + info(MINIMAL_INFO) + "</MerchantStatement>";

        assertThatThrownBy(() -> parser.parse(body))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("MerchantStatement has more than one Info");
    }

    @Test
    void should_reject_when_parsed_given_transaction_with_repeated_field() {
        String transaction = "<Transaction>" + elements(MINIMAL_TRANSACTION) + "<amount>2.00</amount></Transaction>";

        assertThatThrownBy(() -> parser.parse(statement(transaction)))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Transaction[1] has more than one amount");
    }

    @Test
    void should_reject_when_parsed_given_both_spellings_of_one_field() {
        String transaction = transaction("cardAssociationFee", "1.00", "cardAsosiationFee", "1.00");

        assertThatThrownBy(() -> parser.parse(statement(transaction)))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("Transaction[1] has more than one cardAssociationFee or cardAsosiationFee");
    }

    @Test
    void should_reject_without_reading_it_when_parsed_given_document_type_with_external_entity() {
        String body = "<?xml version=\"1.0\"?><!DOCTYPE MerchantStatement SYSTEM \"" + server.baseUri() + "/schema.dtd\" ["
                + "<!ENTITY secret SYSTEM \"file:///etc/hostname\">"
                + "<!ENTITY remote SYSTEM \"" + server.baseUri() + "/entity\">]>"
                + "<MerchantStatement><Info><accountId>&secret;&remote;</accountId></Info></MerchantStatement>";

        assertThatThrownBy(() -> parser.parse(body))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("The body declares a document type, which this client does not read");
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void should_reject_when_parsed_given_elements_nested_deeper_than_a_statement_needs() {
        String nested = "<x>".repeat(20) + "</x>".repeat(20);
        String body = statement(transaction("note", nested));

        assertThatThrownBy(() -> parser.parse(body))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessage("The body nests elements deeper than a merchant statement does");
    }

    private CardTransaction only(String transaction) {
        List<CardTransaction> transactions = parser.parse(statement(transaction)).transactions();
        assertThat(transactions).hasSize(1);
        return transactions.get(0);
    }

    private static String statement(String transactions) {
        return statement(MINIMAL_INFO, transactions);
    }

    private static String statement(Map<String, String> info, String transactions) {
        return "<MerchantStatement>" + info(info) + "<TransactionList>" + transactions + "</TransactionList></MerchantStatement>";
    }

    private static String info(Map<String, String> fields) {
        return "<Info>" + elements(fields) + "</Info>";
    }

    private static String transaction(String... namesAndValues) {
        Map<String, String> fields = new LinkedHashMap<>(MINIMAL_TRANSACTION);
        fields.putAll(ordered(namesAndValues));
        return "<Transaction>" + elements(fields) + "</Transaction>";
    }

    private static String elements(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(field -> "<" + field.getKey() + ">" + field.getValue() + "</" + field.getKey() + ">")
                .collect(Collectors.joining());
    }

    private static Map<String, String> ordered(String... namesAndValues) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            fields.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return fields;
    }
}
