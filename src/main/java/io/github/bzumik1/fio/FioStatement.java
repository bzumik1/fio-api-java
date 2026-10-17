package io.github.bzumik1.fio;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * One download of account movements: the account and period, and the movements in it.
 *
 * @param info the account and the period or statement the download covers
 * @param transactions the movements, oldest first as the bank sends them
 */
public record FioStatement(FioAccountInfo info, List<FioTransaction> transactions) {

    /**
     * Validates the components and copies the movements.
     *
     * @param info the info block
     * @param transactions the movements
     */
    public FioStatement {
        requireNonNull(info, "info");
        transactions = List.copyOf(transactions);
    }
}
