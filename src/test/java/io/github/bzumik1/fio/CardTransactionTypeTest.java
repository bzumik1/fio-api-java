package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CardTransactionTypeTest {

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
