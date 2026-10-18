package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadFormatTest {

    @Test
    void should_permit_only_the_three_format_families_when_inspected_given_sealed_interface() {
        assertThat(DownloadFormat.class.isSealed()).isTrue();
        assertThat(DownloadFormat.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(TransactionFormat.class, StatementFormat.class, MerchantFormat.class);
    }

    @Test
    void should_describe_every_constant_the_same_way_when_read_through_the_interface_given_each_family() {
        List<DownloadFormat> formats = new ArrayList<>();
        formats.addAll(List.of(TransactionFormat.values()));
        formats.addAll(List.of(StatementFormat.values()));
        formats.addAll(List.of(MerchantFormat.values()));

        for (DownloadFormat format : formats) {
            assertThat(format.extension()).isNotBlank();
            assertThat(format.mediaType()).contains("/");
            assertThat(format.charset()).isNotNull();
        }
        assertThat(fileName(StatementFormat.PDF)).isEqualTo("statement.pdf");
        assertThat(fileName(TransactionFormat.CSV)).isEqualTo("statement.csv");
        assertThat(fileName(MerchantFormat.XML)).isEqualTo("statement.xml");
    }

    private static String fileName(DownloadFormat format) {
        return "statement." + format.extension();
    }
}
