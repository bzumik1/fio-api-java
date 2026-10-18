package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.github.bzumik1.fio.PaymentText.digits;
import static io.github.bzumik1.fio.PaymentText.iban;
import static io.github.bzumik1.fio.PaymentText.optional;
import static io.github.bzumik1.fio.PaymentText.payerAccount;
import static io.github.bzumik1.fio.PaymentText.positive;
import static io.github.bzumik1.fio.PaymentText.required;
import static java.util.Objects.requireNonNull;

/**
 * A SEPA payment in euro, which Fio calls a Europlatba.
 *
 * @param from the payer's account
 * @param toIban the recipient's IBAN
 * @param bic the BIC of the recipient's bank, optional within SEPA
 * @param amount the amount, in euro
 * @param symbols the variable, constant and specific symbols
 * @param date the day the payment is due
 * @param comment the payer's own label, up to 140 characters
 * @param beneficiary the holder of the recipient's account
 * @param remittanceInfo up to three lines of 35 characters for the recipient
 * @param paymentReason the three-digit payment reason code, needed only for some recipients in Slovakia
 * @param type standard, priority or instant
 */
public record EuroPayment(
        DomesticAccount from,
        String toIban,
        @Nullable String bic,
        Money amount,
        PaymentSymbols symbols,
        LocalDate date,
        @Nullable String comment,
        Beneficiary beneficiary,
        List<String> remittanceInfo,
        @Nullable String paymentReason,
        Type type) implements PaymentOrder {

    private static final int REMITTANCE_LINES = 3;

    /**
     * Validates the components.
     *
     * @param from the payer's account
     * @param toIban the recipient's IBAN
     * @param bic the BIC
     * @param amount the amount
     * @param symbols the symbols
     * @param date the due date
     * @param comment the comment
     * @param beneficiary the beneficiary
     * @param remittanceInfo the lines for the recipient
     * @param paymentReason the payment reason
     * @param type the type
     * @throws IllegalArgumentException when the payer's account is not a Fio account, the amount is not positive
     *         euro with at most two decimal places, the IBAN or BIC is malformed, a text is too long or blank, there
     *         are more than three remittance lines or the payment reason is not three digits
     */
    public EuroPayment {
        from = payerAccount(from);
        toIban = iban(toIban);
        bic = bic == null ? null : PaymentText.bic(bic);
        amount = positive(amount);
        if (!amount.currency().getCurrencyCode().equals("EUR")) {
            throw new IllegalArgumentException("A SEPA payment is in euro, not " + amount.currency());
        }
        requireNonNull(symbols, "symbols");
        requireNonNull(date, "date");
        comment = optional(comment, 140, "comment");
        requireNonNull(beneficiary, "beneficiary");
        remittanceInfo = remittanceLines(remittanceInfo, REMITTANCE_LINES);
        paymentReason = digits(paymentReason, 3, "payment reason");
        requireNonNull(type, "type");
    }

    /**
     * Starts building a payment.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    static List<String> remittanceLines(List<String> lines, int maximumLines) {
        requireNonNull(lines, "remittanceInfo");
        if (lines.size() > maximumLines) {
            throw new IllegalArgumentException(
                    "At most " + maximumLines + " remittance lines are allowed, not " + lines.size());
        }
        List<String> checked = new ArrayList<>();
        for (String line : lines) {
            checked.add(required(line, 35, "remittance line"));
        }
        return List.copyOf(checked);
    }

    /**
     * The kind of SEPA order, which Fio calls the payment type.
     */
    public enum Type {

        /** An ordinary SEPA payment, code 431008. */
        STANDARD("431008"),
        /** A priority SEPA payment, code 431009. */
        PRIORITY("431009"),
        /** An instant SEPA payment, code 431018. */
        INSTANT("431018");

        private final String code;

        Type(String code) {
            this.code = code;
        }

        /**
         * Returns the code Fio uses for the type.
         *
         * @return the code
         */
        public String code() {
            return code;
        }
    }

    /**
     * Builds a {@link EuroPayment}. The payer's account, the IBAN, the amount, the date and the beneficiary's
     * name are required; the type is {@link Type#STANDARD} unless set.
     */
    public static final class Builder {

        private @Nullable DomesticAccount from;
        private @Nullable String toIban;
        private @Nullable String bic;
        private @Nullable Money amount;
        private @Nullable String variableSymbol;
        private @Nullable String constantSymbol;
        private @Nullable String specificSymbol;
        private @Nullable LocalDate date;
        private @Nullable String comment;
        private @Nullable Beneficiary beneficiary;
        private final List<String> remittanceInfo = new ArrayList<>();
        private @Nullable String paymentReason;
        private Type type = Type.STANDARD;

        private Builder() {
        }

        /**
         * Sets the payer's account.
         *
         * @param from the account the payment leaves
         * @return this builder
         */
        public Builder from(DomesticAccount from) {
            this.from = requireNonNull(from, "from");
            return this;
        }

        /**
         * Sets the payer's account from its number.
         *
         * @param from the account number
         * @return this builder
         */
        public Builder from(String from) {
            return from(DomesticAccount.parse(from));
        }

        /**
         * Sets the recipient's IBAN.
         *
         * @param iban the IBAN, spaces allowed
         * @return this builder
         */
        public Builder to(String iban) {
            this.toIban = requireNonNull(iban, "iban");
            return this;
        }

        /**
         * Sets the BIC of the recipient's bank.
         *
         * @param bic the BIC
         * @return this builder
         */
        public Builder bic(String bic) {
            this.bic = requireNonNull(bic, "bic");
            return this;
        }

        /**
         * Sets the amount.
         *
         * @param amount the amount in euro
         * @return this builder
         */
        public Builder amount(Money amount) {
            this.amount = requireNonNull(amount, "amount");
            return this;
        }

        /**
         * Sets the variable symbol.
         *
         * @param variableSymbol up to 10 digits
         * @return this builder
         */
        public Builder variableSymbol(String variableSymbol) {
            this.variableSymbol = requireNonNull(variableSymbol, "variableSymbol");
            return this;
        }

        /**
         * Sets the constant symbol.
         *
         * @param constantSymbol up to 4 digits
         * @return this builder
         */
        public Builder constantSymbol(String constantSymbol) {
            this.constantSymbol = requireNonNull(constantSymbol, "constantSymbol");
            return this;
        }

        /**
         * Sets the specific symbol.
         *
         * @param specificSymbol up to 10 digits
         * @return this builder
         */
        public Builder specificSymbol(String specificSymbol) {
            this.specificSymbol = requireNonNull(specificSymbol, "specificSymbol");
            return this;
        }

        /**
         * Sets the day the payment is due.
         *
         * @param date the due date
         * @return this builder
         */
        public Builder date(LocalDate date) {
            this.date = requireNonNull(date, "date");
            return this;
        }

        /**
         * Sets the payer's own label of the payment.
         *
         * @param comment up to 140 characters
         * @return this builder
         */
        public Builder comment(String comment) {
            this.comment = requireNonNull(comment, "comment");
            return this;
        }

        /**
         * Sets the holder of the recipient's account.
         *
         * @param beneficiary the beneficiary
         * @return this builder
         */
        public Builder beneficiary(Beneficiary beneficiary) {
            this.beneficiary = requireNonNull(beneficiary, "beneficiary");
            return this;
        }

        /**
         * Sets the holder of the recipient's account by name.
         *
         * @param name the name, up to 35 characters
         * @return this builder
         */
        public Builder beneficiary(String name) {
            return beneficiary(Beneficiary.named(name));
        }

        /**
         * Adds a line of information for the recipient.
         *
         * @param line up to 35 characters; at most three lines
         * @return this builder
         */
        public Builder remittanceInfo(String line) {
            remittanceInfo.add(requireNonNull(line, "line"));
            return this;
        }

        /**
         * Sets the payment reason code.
         *
         * @param paymentReason three digits, as listed in Fio's documentation
         * @return this builder
         */
        public Builder paymentReason(String paymentReason) {
            this.paymentReason = requireNonNull(paymentReason, "paymentReason");
            return this;
        }

        /**
         * Sets the kind of order.
         *
         * @param type the type, {@link Type#STANDARD} by default
         * @return this builder
         */
        public Builder type(Type type) {
            this.type = requireNonNull(type, "type");
            return this;
        }

        /**
         * Builds the payment.
         *
         * @return the payment
         * @throws IllegalStateException when a required part is missing
         * @throws IllegalArgumentException when a part is invalid
         */
        public EuroPayment build() {
            return new EuroPayment(
                    required(from, "payer's account"),
                    required(toIban, "recipient's IBAN"),
                    bic,
                    required(amount, "amount"),
                    new PaymentSymbols(variableSymbol, constantSymbol, specificSymbol),
                    required(date, "date"),
                    comment,
                    required(beneficiary, "beneficiary"),
                    remittanceInfo,
                    paymentReason,
                    type);
        }
    }
}
