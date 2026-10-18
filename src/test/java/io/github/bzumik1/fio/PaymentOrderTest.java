package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOrderTest {

    @Test
    void should_permit_only_the_three_payment_kinds_when_inspected_given_sealed_interface() {
        assertThat(PaymentOrder.class.isSealed()).isTrue();
        assertThat(PaymentOrder.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(DomesticPayment.class, EuroPayment.class, ForeignPayment.class);
    }
}
