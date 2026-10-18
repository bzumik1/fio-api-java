package io.github.bzumik1.fio;

import java.util.Arrays;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Payment orders submitted together. Fio requires the orders in the sequence domestic, euro, foreign; the batch
 * keeps them in the sequence they were added and the client reorders them when it writes the file.
 *
 * <p>A token belongs to one account, and the bank refuses orders from any other. The client cannot know which
 * account that is, so it keeps a batch consistent: every order is {@linkplain PaymentOrder#from() from} the same
 * account and in the same currency, and a batch with orders from two accounts, or in two currencies, is refused
 * before anything is sent. Euro payments therefore go in a batch of their own, submitted with the euro account's
 * token.
 *
 * @param orders the orders, at least one, all from the same account and in the same currency
 */
public record PaymentBatch(List<PaymentOrder> orders) {

    /**
     * Validates and copies the orders.
     *
     * @param orders the orders
     * @throws IllegalArgumentException when there are no orders, or they are not all from the same account and in
     *         the same currency
     */
    public PaymentBatch {
        requireNonNull(orders, "orders");
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i) == null) {
                throw new NullPointerException("orders[" + i + "] is null");
            }
        }
        orders = List.copyOf(orders);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("A batch holds at least one payment order");
        }
        PaymentOrder first = orders.get(0);
        for (PaymentOrder order : orders) {
            if (!order.from().number().equals(first.from().number())) {
                throw new IllegalArgumentException("Every order in a batch is from the same account: "
                        + first.from() + " and " + order.from());
            }
            if (!order.amount().currency().equals(first.amount().currency())) {
                throw new IllegalArgumentException("Every order in a batch is in the same currency: "
                        + first.amount().currency() + " and " + order.amount().currency());
            }
        }
    }

    /**
     * Creates a batch of the given orders.
     *
     * @param orders the orders, at least one, all from the same account and in the same currency
     * @return the batch
     * @throws IllegalArgumentException when there are no orders, or they are not all from the same account and in
     *         the same currency
     */
    public static PaymentBatch of(PaymentOrder... orders) {
        return new PaymentBatch(Arrays.asList(requireNonNull(orders, "orders")));
    }
}
