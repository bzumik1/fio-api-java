package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ForeignAccountTest {

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
}
