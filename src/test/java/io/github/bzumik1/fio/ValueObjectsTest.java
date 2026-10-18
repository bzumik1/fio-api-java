package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    private static final Charset WINDOWS_1250 = Charset.forName("windows-1250");

    private static final List<String> DOCUMENTED_TRANSACTION_TYPES = List.of(
            "Příjem převodem uvnitř banky",
            "Platba převodem uvnitř banky",
            "Vklad pokladnou",
            "Výběr pokladnou",
            "Vklad v hotovosti",
            "Výběr v hotovosti",
            "Platba",
            "Příjem",
            "Bezhotovostní platba",
            "Bezhotovostní příjem",
            "Platba kartou",
            "Bezhotovostní platba",
            "Úrok z úvěru",
            "Sankční poplatek",
            "Posel – předání",
            "Posel – příjem",
            "Převod uvnitř konta",
            "Připsaný úrok",
            "Vyplacený úrok",
            "Odvod daně z úroků",
            "Evidovaný úrok",
            "Poplatek",
            "Evidovaný poplatek",
            "Převod mezi bankovními konty (platba)",
            "Převod mezi bankovními konty (příjem)",
            "Neidentifikovaná platba z bankovního konta",
            "Neidentifikovaný příjem na bankovní konto",
            "Vlastní platba z bankovního konta",
            "Vlastní příjem na bankovní konto",
            "Vlastní platba pokladnou",
            "Vlastní příjem pokladnou",
            "Opravný pohyb",
            "Přijatý poplatek",
            "Platba v jiné měně",
            "Poplatek – platební karta",
            "Inkaso",
            "Inkaso ve prospěch účtu",
            "Inkaso z účtu",
            "Příjem inkasa z cizí banky",
            "Evidovaný úrok",
            "Okamžitá příchozí platba",
            "Okamžitá odchozí platba",
            "Poplatek - pojištění hypotéky",
            "Okamžitá příchozí Europlatba",
            "Okamžitá odchozí Europlatba");

    @Test
    void should_print_identifier_when_printed_given_foreign_account() {
        assertThat(new ForeignAccount("AT611904300234573201")).hasToString("AT611904300234573201");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void should_reject_when_created_given_blank_foreign_account(String identifier) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ForeignAccount(identifier))
                .withMessage("A foreign account needs an identifier");
    }

    static Stream<Arguments> counterparties() {
        return Stream.of(
                Arguments.of(Counterparty.none(), true),
                Arguments.of(new Counterparty(null, "Novák", null, null), false),
                Arguments.of(new Counterparty(DomesticAccount.parse("2400222222/2010"), null, null, null), false),
                Arguments.of(new Counterparty(null, null, "Fio banka", null), false),
                Arguments.of(new Counterparty(null, null, null, "FIOBCZPP"), false));
    }

    @ParameterizedTest
    @MethodSource("counterparties")
    void should_tell_whether_anything_is_known_when_asked_given_counterparty(Counterparty counterparty, boolean empty) {
        assertThat(counterparty.isEmpty()).isEqualTo(empty);
    }

    static Stream<Arguments> symbols() {
        return Stream.of(
                Arguments.of(PaymentSymbols.none(), true),
                Arguments.of(PaymentSymbols.ofVariable("0558"), false),
                Arguments.of(new PaymentSymbols(null, "0558", null), false),
                Arguments.of(new PaymentSymbols(null, null, "1"), false));
    }

    @ParameterizedTest
    @MethodSource("symbols")
    void should_tell_whether_any_symbol_is_set_when_asked_given_symbols(PaymentSymbols symbols, boolean empty) {
        assertThat(symbols.isEmpty()).isEqualTo(empty);
    }

    @Test
    void should_set_only_the_variable_symbol_when_created_given_variable_symbol() {
        assertThat(PaymentSymbols.ofVariable("0558")).isEqualTo(new PaymentSymbols("0558", null, null));
    }

    @Test
    void should_keep_leading_zeros_when_created_given_symbols_of_the_longest_lengths() {
        assertThat(new PaymentSymbols("0000000001", "0558", "0000000002"))
                .returns("0000000001", PaymentSymbols::variable)
                .returns("0558", PaymentSymbols::constant)
                .returns("0000000002", PaymentSymbols::specific);
    }

    @ParameterizedTest
    @CsvSource({
            "variable, 12345678901, 10", "variable, abc, 10", "variable, '', 10", "variable, 12 34, 10",
            "constant, 05580, 4", "constant, abc, 4", "constant, '', 4",
            "specific, 12345678901, 10", "specific, 1a, 10", "specific, '', 10"
    })
    void should_reject_naming_the_symbol_when_created_given_symbol_that_is_not_its_digits(String kind, String symbol, int digits) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentSymbols(
                        kind.equals("variable") ? symbol : null,
                        kind.equals("constant") ? symbol : null,
                        kind.equals("specific") ? symbol : null))
                .withMessage("A " + kind + " symbol is up to " + digits + " digits: '" + symbol + "'");
    }

    @Test
    void should_order_by_number_when_compared_given_statements_of_one_year() {
        assertThat(new StatementId(2026, 2)).isGreaterThan(new StatementId(2026, 1));
    }

    @Test
    void should_order_by_year_first_when_compared_given_statements_of_different_years() {
        assertThat(new StatementId(2026, 12)).isLessThan(new StatementId(2027, 1));
    }

    @Test
    void should_compare_as_equal_when_compared_given_same_year_and_number() {
        assertThat(new StatementId(2026, 2)).isEqualByComparingTo(new StatementId(2026, 2)).isEqualTo(new StatementId(2026, 2));
    }

    @Test
    void should_print_year_slash_number_when_printed_given_statement_id() {
        assertThat(new StatementId(2026, 2)).hasToString("2026/2");
    }

    @ParameterizedTest
    @CsvSource({"999, 1", "10000, 1", "2026, 0", "2026, -1"})
    void should_reject_when_created_given_year_or_number_out_of_range(int year, int number) {
        assertThatIllegalArgumentException().isThrownBy(() -> new StatementId(year, number));
    }

    @ParameterizedTest
    @CsvSource(value = {"2026,10|2026|10", "' 2026,10\n'|2026|10", "2012,3|2012|3", "2012,999|2012|999"}, delimiter = '|')
    void should_read_bank_format_when_parsed_given_statement_number_text(String text, int year, int number) {
        assertThat(StatementId.parse(text)).isEqualTo(new StatementId(year, number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026/10", "2026,", ",10", "26,10", "2026,1000", "2026,10,1", "", "2026, 10"})
    void should_reject_as_malformed_when_parsed_given_text_of_another_shape(String text) {
        assertThatThrownBy(() -> StatementId.parse(text))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("Not a statement number: '" + text + "'")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026,0", "0999,1", "2026,000"})
    void should_reject_as_malformed_with_the_cause_when_parsed_given_year_or_number_out_of_range(String text) {
        assertThatThrownBy(() -> StatementId.parse(text))
                .isExactlyInstanceOf(MalformedResponseException.class)
                .hasMessage("Not a statement number: '" + text + "'")
                .hasCauseExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_keep_its_own_copy_when_created_given_array_changed_afterwards() {
        byte[] bytes = "ahoj".getBytes(StandardCharsets.UTF_8);
        Download document = new Download("text/plain", StandardCharsets.UTF_8, bytes);

        bytes[0] = 'x';

        assertThat(document.asText()).isEqualTo("ahoj");
    }

    @Test
    void should_hand_out_copies_when_content_read_given_document() {
        Download document = new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8));

        document.content()[0] = 'y';

        assertThat(document.asText()).isEqualTo("ahoj");
    }

    @Test
    void should_print_media_type_charset_and_size_when_printed_given_document() {
        assertThat(new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8)))
                .hasToString("Download[text/plain, UTF-8, 4 bytes]");
    }

    @Test
    void should_equal_with_same_hash_when_compared_given_same_media_type_charset_and_bytes() {
        Download document = new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8));
        Download same = new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8));

        assertThat(document).isEqualTo(same).hasSameHashCodeAs(same);
    }

    static Stream<Object> differentDocuments() {
        return Stream.of(
                new Download("text/csv", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8)),
                new Download("text/plain", StandardCharsets.UTF_8, "ahoi".getBytes(StandardCharsets.UTF_8)),
                new Download("text/plain", StandardCharsets.ISO_8859_1, "ahoj".getBytes(StandardCharsets.UTF_8)),
                "ahoj");
    }

    @ParameterizedTest
    @MethodSource("differentDocuments")
    void should_differ_when_compared_given_other_media_type_charset_bytes_or_type(Object other) {
        assertThat(new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8))).isNotEqualTo(other);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/csv; charset=windows-1250", "text/csv;charset=\"windows-1250\"", "text/csv; CHARSET=Windows-1250"})
    void should_decode_with_named_charset_when_read_given_media_type_naming_a_charset(String mediaType) {
        Download document = Download.of(mediaType, StandardCharsets.UTF_8, "Příjem".getBytes(WINDOWS_1250));

        assertThat(document).returns(WINDOWS_1250, Download::charset).returns("Příjem", Download::asText);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/csv", "text/csv;charset=nonsense-9"})
    void should_use_documented_charset_when_read_given_media_type_without_a_known_charset(String mediaType) {
        assertThat(Download.of(mediaType, WINDOWS_1250, new byte[0]).charset()).isEqualTo(WINDOWS_1250);
    }

    @ParameterizedTest
    @MethodSource("documentedTransactionTypes")
    void should_find_the_type_with_that_label_when_looked_up_given_label_from_the_documentation(String label) {
        TransactionType type = TransactionType.fromLabel(" " + label + " ");

        assertThat(type).isNotEqualTo(TransactionType.OTHER);
        assertThat(type.label()).isEqualTo(label);
    }

    static Stream<String> documentedTransactionTypes() {
        return DOCUMENTED_TRANSACTION_TYPES.stream();
    }

    @Test
    void should_have_one_type_for_each_distinct_documented_label_when_listed_given_documentation_list() {
        Set<String> distinct = new TreeSet<>(DOCUMENTED_TRANSACTION_TYPES);
        Set<String> labels = Stream.of(TransactionType.values())
                .filter(type -> type != TransactionType.OTHER)
                .map(TransactionType::label)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(DOCUMENTED_TRANSACTION_TYPES).hasSize(45);
        assertThat(distinct).hasSize(43);
        assertThat(labels).isEqualTo(distinct);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nový typ pohybu", "", "platba", "Posel - předání"})
    void should_map_to_other_when_looked_up_given_label_the_documentation_does_not_list(String label) {
        assertThat(TransactionType.fromLabel(label)).isEqualTo(TransactionType.OTHER);
    }

    @Test
    void should_have_empty_label_when_asked_given_other_type() {
        assertThat(TransactionType.OTHER.label()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "CSV, csv, text/csv, UTF-8",
            "GPC, gpc, text/plain, windows-1250",
            "HTML, html, text/html, UTF-8",
            "JSON, json, application/json, UTF-8",
            "OFX, ofx, application/x-ofx, UTF-8",
            "XML, xml, application/xml, UTF-8"
    })
    void should_name_extension_media_type_and_charset_when_asked_given_transaction_format(
            TransactionFormat format, String extension, String mediaType, String charset) {
        assertThat(format)
                .returns(extension, TransactionFormat::extension)
                .returns(mediaType, TransactionFormat::mediaType)
                .returns(Charset.forName(charset), TransactionFormat::charset);
    }

    @ParameterizedTest
    @CsvSource({
            "CSV, csv, text/csv, UTF-8",
            "GPC, gpc, text/plain, windows-1250",
            "HTML, html, text/html, UTF-8",
            "JSON, json, application/json, UTF-8",
            "OFX, ofx, application/x-ofx, UTF-8",
            "XML, xml, application/xml, UTF-8",
            "PDF, pdf, application/pdf, UTF-8",
            "MT940, sta, text/plain, UTF-8",
            "CBA_XML, cba_xml, application/xml, UTF-8",
            "SBA_XML, sba_xml, application/xml, UTF-8"
    })
    void should_name_extension_media_type_and_charset_when_asked_given_statement_format(
            StatementFormat format, String extension, String mediaType, String charset) {
        assertThat(format)
                .returns(extension, StatementFormat::extension)
                .returns(mediaType, StatementFormat::mediaType)
                .returns(Charset.forName(charset), StatementFormat::charset);
    }

    @ParameterizedTest
    @CsvSource({"XML, xml, application/xml, UTF-8", "CSV, csv, text/csv, UTF-8"})
    void should_name_extension_media_type_and_charset_when_asked_given_merchant_format(
            MerchantFormat format, String extension, String mediaType, String charset) {
        assertThat(format)
                .returns(extension, MerchantFormat::extension)
                .returns(mediaType, MerchantFormat::mediaType)
                .returns(Charset.forName(charset), MerchantFormat::charset);
    }

    @ParameterizedTest
    @CsvSource({"ON_US, ON_US", "DOMESTIC, DOMESTIC", "FOREIGN, FOREIGN", "' ON_US ', ON_US", "on_us, OTHER",
            "OTHER, OTHER", "'', OTHER", "INTERNATIONAL, OTHER"})
    void should_map_bank_text_to_type_when_looked_up_given_card_transaction_label(String label, CardTransactionType type) {
        assertThat(CardTransactionType.fromLabel(label)).isEqualTo(type);
    }

    @Test
    void should_leave_type_unknown_when_looked_up_given_no_label() {
        assertThat(CardTransactionType.fromOptionalLabel(null)).isNull();
    }

    @Test
    void should_map_label_when_looked_up_given_optional_label() {
        assertThat(CardTransactionType.fromOptionalLabel("DOMESTIC")).isEqualTo(CardTransactionType.DOMESTIC);
    }

    @Test
    void should_list_every_card_issuer_classification_of_the_schema_when_listed_given_card_transaction_types() {
        assertThat(Stream.of(CardTransactionType.values()).filter(type -> type != CardTransactionType.OTHER).map(Enum::name))
                .containsExactlyElementsOf(ImportSchema.enumeration("pos_type"));
    }
}
