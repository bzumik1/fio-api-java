package io.github.bzumik1.fio;

import java.util.List;

/**
 * Payment orders submitted together. Fio requires the orders in the sequence domestic, euro, foreign; the batch
 * keeps them in the sequence they were added and the client reorders them when it writes the file.
 *
 * @param orders the orders, at least one
 */
public record PaymentBatch(List<PaymentOrder> orders) {

    /**
     * Validates and copies the orders.
     *
     * @param orders the orders
     * @throws IllegalArgumentException when there are no orders
     */
    public PaymentBatch {
        orders = List.copyOf(orders);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("A batch holds at least one payment order");
        }
    }

    /**
     * Creates a batch of the given orders.
     *
     * @param orders the orders, at least one
     * @return the batch
     */
    public static PaymentBatch of(PaymentOrder... orders) {
        return new PaymentBatch(List.of(orders));
    }
}
