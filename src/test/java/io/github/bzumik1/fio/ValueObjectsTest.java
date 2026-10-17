package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Test
    void should_keep_identifier_when_created_given_foreign_account() {
        ForeignAccount account = new ForeignAccount("AT611904300234573201");

        assertThat(account).hasToString("AT611904300234573201");
        assertThat(account).isInstanceOf(BankAccount.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new ForeignAccount(" "));
    }

    @Test
    void should_tell_when_empty_given_counterparty_and_symbols() {
        assertThat(Counterparty.none().isEmpty()).isTrue();
        assertThat(new Counterparty(null, "Novák", null, null).isEmpty()).isFalse();
        assertThat(new Counterparty(DomesticAccount.parse("2400222222/2010"), null, null, null).isEmpty()).isFalse();
        assertThat(new Counterparty(null, null, "Fio banka", null).isEmpty()).isFalse();
        assertThat(new Counterparty(null, null, null, "FIOBCZPP").isEmpty()).isFalse();
        assertThat(PaymentSymbols.none().isEmpty()).isTrue();
        assertThat(PaymentSymbols.variable("0558").isEmpty()).isFalse();
        assertThat(new PaymentSymbols(null, "0558", null).isEmpty()).isFalse();
        assertThat(new PaymentSymbols(null, null, "1").isEmpty()).isFalse();
        assertThat(PaymentSymbols.variable("0558")).isEqualTo(new PaymentSymbols("0558", null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901", "abc", "", "12 34"})
    void should_reject_when_created_given_symbol_that_is_not_digits(String symbol) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PaymentSymbols(symbol, null, null)).withMessageContaining("variable");
        assertThatIllegalArgumentException().isThrownBy(() -> new PaymentSymbols(null, symbol, null)).withMessageContaining("constant");
        assertThatIllegalArgumentException().isThrownBy(() -> new PaymentSymbols(null, null, symbol)).withMessageContaining("specific");
    }

    @Test
    void should_reject_when_created_given_constant_symbol_longer_than_four_digits() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PaymentSymbols(null, "05580", null));
        assertThat(new PaymentSymbols("0", "0558", "0000000002")).isNotNull();
    }

    @Test
    void should_order_by_year_then_number_when_compared_given_statement_ids() {
        assertThat(new StatementId(2026, 2)).isGreaterThan(new StatementId(2026, 1))
                .isLessThan(new StatementId(2027, 1))
                .isEqualByComparingTo(new StatementId(2026, 2))
                .hasToString("2026/2");
        assertThatIllegalArgumentException().isThrownBy(() -> new StatementId(999, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new StatementId(10000, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new StatementId(2026, 0));
    }

    @Test
    void should_read_bank_format_when_parsed_given_statement_number_text() {
        assertThat(StatementId.parse("2026,10\n")).isEqualTo(new StatementId(2026, 10));
        assertThatThrownBy(() -> StatementId.parse("2026/10")).isInstanceOf(MalformedResponseException.class);
        assertThatThrownBy(() -> StatementId.parse("2026,0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_copy_content_when_created_and_read_given_document() {
        byte[] bytes = "ahoj".getBytes(StandardCharsets.UTF_8);
        FioDocument document = new FioDocument("text/plain", bytes);
        bytes[0] = 'x';

        assertThat(document.asText()).isEqualTo("ahoj");
        document.content()[0] = 'y';
        assertThat(document.asText()).isEqualTo("ahoj");
        assertThat(document).hasToString("FioDocument[text/plain, 4 bytes]")
                .isEqualTo(new FioDocument("text/plain", "ahoj".getBytes(StandardCharsets.UTF_8)))
                .hasSameHashCodeAs(new FioDocument("text/plain", "ahoj".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(new FioDocument("text/csv", "ahoj".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(new FioDocument("text/plain", "ahoi".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo("ahoj");
    }

    @Test
    void should_decode_with_named_charset_when_read_given_media_type_with_charset() {
        byte[] windows1250 = "Příjem".getBytes(Charset.forName("windows-1250"));

        assertThat(new FioDocument("text/csv; charset=windows-1250", windows1250).asText()).isEqualTo("Příjem");
        assertThat(new FioDocument("text/csv;charset=\"windows-1250\"", windows1250).charset()).isEqualTo(Charset.forName("windows-1250"));
        assertThat(new FioDocument("text/csv", windows1250).charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(new FioDocument("text/csv;charset=nonsense-9", windows1250).charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void should_map_label_back_to_type_when_looked_up_given_documented_label(TransactionType type) {
        TransactionType expected = type == TransactionType.OTHER ? TransactionType.OTHER : type;

        assertThat(TransactionType.fromLabel(" " + type.label() + " ")).isEqualTo(expected);
    }

    @Test
    void should_map_unknown_label_to_other_when_looked_up_given_new_type() {
        assertThat(TransactionType.fromLabel("Nový typ pohybu")).isEqualTo(TransactionType.OTHER);
        assertThat(TransactionType.OTHER.label()).isEmpty();
    }

    @Test
    void should_name_extension_and_media_type_given_every_format() {
        for (TransactionFormat format : TransactionFormat.values()) {
            assertThat(format.extension()).isEqualTo(format.name().toLowerCase());
            assertThat(format.mediaType()).contains("/");
        }
        for (StatementFormat format : StatementFormat.values()) {
            assertThat(format.extension()).isNotBlank();
            assertThat(format.mediaType()).contains("/");
        }
        assertThat(StatementFormat.MT940.extension()).isEqualTo("sta");
        assertThat(StatementFormat.CBA_XML.extension()).isEqualTo("cba_xml");
        for (MerchantFormat format : MerchantFormat.values()) {
            assertThat(format.extension()).isEqualTo(format.name().toLowerCase());
            assertThat(format.mediaType()).contains("/");
        }
    }
}
