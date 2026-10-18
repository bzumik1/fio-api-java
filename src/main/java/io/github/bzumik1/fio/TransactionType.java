package io.github.bzumik1.fio;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * The type of an account movement, as Fio's documentation lists them. The bank sends the Czech label; a label this
 * library does not know maps to {@link #OTHER} and stays readable in {@link Transaction#typeLabel()}. Constants
 * may be added in a minor release when Fio documents new types, so a {@code switch} over this type needs a
 * {@code default} branch.
 */
public enum TransactionType {

    /** Příjem převodem uvnitř banky. */
    INCOMING_TRANSFER_WITHIN_BANK("Příjem převodem uvnitř banky"),
    /** Platba převodem uvnitř banky. */
    OUTGOING_TRANSFER_WITHIN_BANK("Platba převodem uvnitř banky"),
    /** Vklad pokladnou. */
    CASH_DESK_DEPOSIT("Vklad pokladnou"),
    /** Výběr pokladnou. */
    CASH_DESK_WITHDRAWAL("Výběr pokladnou"),
    /** Vklad v hotovosti. */
    CASH_DEPOSIT("Vklad v hotovosti"),
    /** Výběr v hotovosti. */
    CASH_WITHDRAWAL("Výběr v hotovosti"),
    /** Platba. */
    PAYMENT("Platba"),
    /** Příjem. */
    INCOME("Příjem"),
    /** Bezhotovostní platba. */
    CASHLESS_PAYMENT("Bezhotovostní platba"),
    /** Bezhotovostní příjem. */
    CASHLESS_INCOME("Bezhotovostní příjem"),
    /** Platba kartou. */
    CARD_PAYMENT("Platba kartou"),
    /** Úrok z úvěru. */
    LOAN_INTEREST("Úrok z úvěru"),
    /** Sankční poplatek. */
    PENALTY_FEE("Sankční poplatek"),
    /** Posel – předání. */
    COURIER_HANDOVER("Posel – předání"),
    /** Posel – příjem. */
    COURIER_RECEIPT("Posel – příjem"),
    /** Převod uvnitř konta. */
    TRANSFER_WITHIN_ACCOUNT("Převod uvnitř konta"),
    /** Připsaný úrok. */
    INTEREST_CREDITED("Připsaný úrok"),
    /** Vyplacený úrok. */
    INTEREST_PAID("Vyplacený úrok"),
    /** Odvod daně z úroků. */
    INTEREST_TAX("Odvod daně z úroků"),
    /** Evidovaný úrok. */
    INTEREST_RECORDED("Evidovaný úrok"),
    /** Poplatek. */
    FEE("Poplatek"),
    /** Evidovaný poplatek. */
    FEE_RECORDED("Evidovaný poplatek"),
    /** Převod mezi bankovními konty (platba). */
    TRANSFER_BETWEEN_BANK_ACCOUNTS_PAYMENT("Převod mezi bankovními konty (platba)"),
    /** Převod mezi bankovními konty (příjem). */
    TRANSFER_BETWEEN_BANK_ACCOUNTS_INCOME("Převod mezi bankovními konty (příjem)"),
    /** Neidentifikovaná platba z bankovního konta. */
    UNIDENTIFIED_PAYMENT_FROM_BANK_ACCOUNT("Neidentifikovaná platba z bankovního konta"),
    /** Neidentifikovaný příjem na bankovní konto. */
    UNIDENTIFIED_INCOME_TO_BANK_ACCOUNT("Neidentifikovaný příjem na bankovní konto"),
    /** Vlastní platba z bankovního konta. */
    OWN_PAYMENT_FROM_BANK_ACCOUNT("Vlastní platba z bankovního konta"),
    /** Vlastní příjem na bankovní konto. */
    OWN_INCOME_TO_BANK_ACCOUNT("Vlastní příjem na bankovní konto"),
    /** Vlastní platba pokladnou. */
    OWN_CASH_DESK_PAYMENT("Vlastní platba pokladnou"),
    /** Vlastní příjem pokladnou. */
    OWN_CASH_DESK_INCOME("Vlastní příjem pokladnou"),
    /** Opravný pohyb. */
    CORRECTION("Opravný pohyb"),
    /** Přijatý poplatek. */
    FEE_RECEIVED("Přijatý poplatek"),
    /** Platba v jiné měně. */
    PAYMENT_IN_OTHER_CURRENCY("Platba v jiné měně"),
    /** Poplatek – platební karta. */
    CARD_FEE("Poplatek – platební karta"),
    /** Inkaso. */
    DIRECT_DEBIT("Inkaso"),
    /** Inkaso ve prospěch účtu. */
    DIRECT_DEBIT_TO_ACCOUNT("Inkaso ve prospěch účtu"),
    /** Inkaso z účtu. */
    DIRECT_DEBIT_FROM_ACCOUNT("Inkaso z účtu"),
    /** Příjem inkasa z cizí banky. */
    DIRECT_DEBIT_INCOME_FROM_OTHER_BANK("Příjem inkasa z cizí banky"),
    /** Okamžitá příchozí platba. */
    INSTANT_INCOMING_PAYMENT("Okamžitá příchozí platba"),
    /** Okamžitá odchozí platba. */
    INSTANT_OUTGOING_PAYMENT("Okamžitá odchozí platba"),
    /** Poplatek - pojištění hypotéky. */
    MORTGAGE_INSURANCE_FEE("Poplatek - pojištění hypotéky"),
    /** Okamžitá příchozí Europlatba. */
    INSTANT_INCOMING_EURO_PAYMENT("Okamžitá příchozí Europlatba"),
    /** Okamžitá odchozí Europlatba. */
    INSTANT_OUTGOING_EURO_PAYMENT("Okamžitá odchozí Europlatba"),
    /** A label this library does not know. */
    OTHER("");

    private static final Map<String, TransactionType> BY_LABEL = Stream.of(values())
            .filter(type -> type != OTHER)
            .collect(Collectors.toUnmodifiableMap(TransactionType::label, Function.identity()));

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    /**
     * Returns the Czech label the bank sends for the type; empty for {@link #OTHER}.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Finds the type by the label the bank sent.
     *
     * @param label the label, surrounding whitespace ignored
     * @return the type, or {@link #OTHER} when the label is unknown
     */
    public static TransactionType fromLabel(String label) {
        return BY_LABEL.getOrDefault(requireNonNull(label, "label").strip(), OTHER);
    }
}
