package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTypeTest {

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
}
