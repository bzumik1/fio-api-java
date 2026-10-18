package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankAccountTest {

    @Test
    void should_permit_only_domestic_and_foreign_accounts_when_inspected_given_sealed_interface() {
        assertThat(BankAccount.class.isSealed()).isTrue();
        assertThat(BankAccount.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(DomesticAccount.class, ForeignAccount.class);
    }
}
