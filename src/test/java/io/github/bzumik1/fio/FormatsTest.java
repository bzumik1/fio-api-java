package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class FormatsTest {

    private static final String EXTENSION = "[a-z0-9_]+";
    private static final String BARE_MEDIA_TYPE = "[a-z]+/[a-z0-9.+-]+";

    @Test
    void should_name_distinct_extensions_bare_media_types_and_utf_8_except_for_gpc_when_asked_given_statement_formats() {
        assertThat(Stream.of(StatementFormat.values()).map(StatementFormat::extension)).doesNotHaveDuplicates();
        assertSoftly(softly -> {
            for (StatementFormat format : StatementFormat.values()) {
                softly.assertThat(format.extension()).as("%s extension", format).matches(EXTENSION);
                softly.assertThat(format.mediaType()).as("%s media type", format).matches(BARE_MEDIA_TYPE);
                softly.assertThat(format.charset()).as("%s charset", format)
                        .isEqualTo(format == StatementFormat.GPC ? Charset.forName("windows-1250") : StandardCharsets.UTF_8);
            }
        });
    }

    @Test
    void should_agree_with_the_statement_format_of_the_same_name_when_asked_given_transaction_formats() {
        assertSoftly(softly -> {
            for (TransactionFormat format : TransactionFormat.values()) {
                StatementFormat same = StatementFormat.valueOf(format.name());
                softly.assertThat(format.extension()).as("%s extension", format).isEqualTo(same.extension());
                softly.assertThat(format.mediaType()).as("%s media type", format).isEqualTo(same.mediaType());
                softly.assertThat(format.charset()).as("%s charset", format).isEqualTo(same.charset());
            }
        });
    }

    @Test
    void should_agree_with_the_transaction_format_of_the_same_name_when_asked_given_merchant_formats() {
        assertSoftly(softly -> {
            for (MerchantFormat format : MerchantFormat.values()) {
                TransactionFormat same = TransactionFormat.valueOf(format.name());
                softly.assertThat(format.extension()).as("%s extension", format).isEqualTo(same.extension());
                softly.assertThat(format.mediaType()).as("%s media type", format).isEqualTo(same.mediaType());
                softly.assertThat(format.charset()).as("%s charset", format).isEqualTo(same.charset());
            }
        });
    }
}
