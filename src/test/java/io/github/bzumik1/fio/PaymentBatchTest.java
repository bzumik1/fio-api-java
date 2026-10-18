package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.bzumik1.fio.PaymentSamples.domestic;
import static io.github.bzumik1.fio.PaymentSamples.domesticBuilder;
import static io.github.bzumik1.fio.PaymentSamples.domesticInEuro;
import static io.github.bzumik1.fio.PaymentSamples.euro;
import static io.github.bzumik1.fio.PaymentSamples.foreignBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentBatchTest {

    @Test
    void should_keep_orders_in_sequence_added_when_created_given_every_kind_of_order_in_euro() {
        ForeignPayment foreign = foreignBuilder().amount(Money.of("100.00", "EUR")).build();
        DomesticPayment domestic = domesticInEuro();
        EuroPayment euro = euro();

        assertThat(PaymentBatch.of(foreign, domestic, euro).orders()).containsExactly(foreign, domestic, euro);
    }

    @Test
    void should_keep_own_copy_when_created_given_mutable_list() {
        List<PaymentOrder> orders = new ArrayList<>(List.of(domestic()));

        PaymentBatch batch = new PaymentBatch(orders);
        orders.add(euro());

        assertThat(batch.orders()).containsExactly(domestic());
    }

    @Test
    void should_refuse_changes_when_orders_changed_given_created_batch() {
        PaymentBatch batch = PaymentBatch.of(domestic());

        assertThatThrownBy(() -> batch.orders().add(euro())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_reject_when_created_given_no_orders() {
        assertThatIllegalArgumentException().isThrownBy(PaymentBatch::of)
                .withMessage("A batch holds at least one payment order");
    }

    @Test
    void should_reject_when_created_given_missing_order() {
        assertThatNullPointerException().isThrownBy(() -> PaymentBatch.of(domestic(), null));
    }

    @Test
    void should_name_the_position_when_created_given_null_order_in_the_list() {
        List<PaymentOrder> orders = new ArrayList<>();
        orders.add(domestic());
        orders.add(null);

        assertThatNullPointerException().isThrownBy(() -> new PaymentBatch(orders)).withMessage("orders[1] is null");
        assertThatNullPointerException().isThrownBy(() -> PaymentBatch.of(domestic(), null)).withMessage("orders[1] is null");
        assertThatNullPointerException().isThrownBy(() -> PaymentBatch.of((PaymentOrder[]) null)).withMessage("orders");
        assertThatNullPointerException().isThrownBy(() -> new PaymentBatch(null)).withMessage("orders");
    }

    @Test
    void should_reject_naming_both_accounts_when_created_given_orders_from_two_accounts() {
        DomesticPayment fromAnotherAccount = domesticBuilder().from("2400222222/2010").build();

        assertThatIllegalArgumentException().isThrownBy(() -> PaymentBatch.of(domestic(), domestic(), fromAnotherAccount))
                .withMessage("Every order in a batch is from the same account: 1234562 and 2400222222/2010");
    }

    @Test
    void should_keep_orders_when_created_given_same_account_written_with_and_without_bank_code() {
        DomesticPayment withBankCode = domesticBuilder().from("1234562/2010").build();

        assertThat(PaymentBatch.of(domestic(), withBankCode).orders()).containsExactly(domestic(), withBankCode);
    }

    @Test
    void should_reject_naming_both_currencies_when_created_given_orders_in_two_currencies() {
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentBatch.of(domestic(), domestic(), euro()))
                .withMessage("Every order in a batch is in the same currency: CZK and EUR");
    }
}
