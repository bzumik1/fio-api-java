package io.github.bzumik1.fio;

/**
 * The account of a counterparty, as Fio identifies it: a Czech account number with its bank code, or an account
 * abroad.
 */
public sealed interface BankAccount permits DomesticAccount, ForeignAccount {
}
