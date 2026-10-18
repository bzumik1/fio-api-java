package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.bzumik1.fio.PaymentSamples.domestic;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.PaymentSamples.foreign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PaymentBatchTest {

    @Test
    void should_keep_orders_in_sequence_added_when_created_given_mixed_orders() {
        ForeignPayment foreign = foreign();
        DomesticPayment domestic = domestic();
        EuroPayment euro = euro();

        assertThat(PaymentBatch.of(foreign, domestic, euro).orders()).containsExactly(foreign, domestic, euro);
    }

    @Test
    void should_keep_own_copy_when_created_given_mutable_list() {
        List<PaymentOrder> orders = new ArrayList<>(List.of(domestic()));

        PaymentBatch batch = new PaymentBatch(orders);
        orders.add(euro());

        assertThat(batch.orders()).hasSize(1);
    }

    @Test
    void should_reject_when_created_given_no_orders_or_missing_order() {
        assertThatIllegalArgumentException().isThrownBy(PaymentBatch::of)
                .withMessage("A batch holds at least one payment order");
        assertThatNullPointerException().isThrownBy(() -> PaymentBatch.of(domestic(), null));
    }
}
