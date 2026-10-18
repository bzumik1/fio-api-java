package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

import static io.github.bzumik1.fio.PaymentText.optional;
import static io.github.bzumik1.fio.PaymentText.payerAccount;
import static io.github.bzumik1.fio.PaymentText.positive;
import static io.github.bzumik1.fio.PaymentText.required;
import static java.util.Objects.requireNonNull;

/**
 * A payment within the Czech Republic, or a transfer in a foreign currency between two Fio accounts.
 *
 * @param from the payer's account
 * @param to the recipient's account, with its bank code
 * @param amount the amount, in the currency of the payer's account
 * @param symbols the variable, constant and specific symbols
 * @param date the day the payment is due
 * @param messageForRecipient the message the recipient sees, up to 140 characters
 * @param comment the payer's own label, up to 140 characters (Fio's documentation says 255, its import schema
 *        140, and the schema is what the bank validates against)
 * @param paymentReason the payment reason code from Fio's list, such as {@code 110}, needed only for some
 *        foreign-currency transfers
 * @param type standard, priority, or a direct debit order
 */
public record DomesticPayment(
        DomesticAccount from,
        DomesticAccount to,
        Money amount,
        PaymentSymbols symbols,
        LocalDate date,
        @Nullable String messageForRecipient,
        @Nullable String comment,
        @Nullable String paymentReason,
        Type type) implements PaymentOrder {

    /**
     * Validates the components.
     *
     * @param from the payer's account
     * @param to the recipient's account
     * @param amount the amount
     * @param symbols the symbols
     * @param date the due date
     * @param messageForRecipient the message
     * @param comment the comment
     * @param paymentReason the payment reason
     * @param type the type
     * @throws IllegalArgumentException when the payer's account is not a Fio account, the amount is not positive
     *         or has more than two decimal places or sixteen digits before them, the recipient's account has no
     *         bank code or fails the modulo 11 check, the due date's year is not four digits, a text is too long or
     *         holds a character XML cannot carry, or the payment reason is not a code of Fio's list
     */
    public DomesticPayment {
        from = payerAccount(from);
        requireNonNull(to, "to");
        if (to.bankCode() == null) {
            throw new IllegalArgumentException("The recipient's account needs a bank code: " + to);
        }
        if (!to.hasValidChecksum()) {
            throw new IllegalArgumentException("The recipient's account fails the modulo 11 check, so it is mistyped: " + to);
        }
        amount = positive(amount);
        requireNonNull(symbols, "symbols");
        date = PaymentText.dueDate(date);
        messageForRecipient = optional(messageForRecipient, 140, "message for the recipient");
        comment = optional(comment, 140, "comment");
        paymentReason = PaymentText.paymentReason(paymentReason);
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

    /**
     * The kind of domestic order, which Fio calls the payment type.
     */
    public enum Type {

        /** An ordinary payment, code 431001. */
        STANDARD("431001"),
        /** A priority payment, code 431005. */
        PRIORITY("431005"),
        /**
         * A direct debit order, code 431022: the money is collected the other way round, from the account in
         * {@link DomesticPayment#to()} to the Fio account in {@link DomesticPayment#from()}.
         */
        DIRECT_DEBIT("431022");

        private final String code;

        Type(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    /**
     * Builds a {@link DomesticPayment}. The payer's account, the recipient's account, the amount and the date are
     * required; the type is {@link Type#STANDARD} unless set.
     *
     * <p>The setters that read an account number from text refuse a malformed one at once with an
     * {@link IllegalArgumentException}; {@link #build()} refuses a missing required part with an
     * {@link IllegalStateException} and checks every other part as {@link DomesticPayment} describes.
     */
    public static final class Builder {

        private @Nullable DomesticAccount from;
        private @Nullable DomesticAccount to;
        private @Nullable Money amount;
        private @Nullable String variableSymbol;
        private @Nullable String constantSymbol;
        private @Nullable String specificSymbol;
        private @Nullable LocalDate date;
        private @Nullable String messageForRecipient;
        private @Nullable String comment;
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
         * Sets the payer's account from its number, such as {@code 2400222222} or {@code 2400222222/2010}.
         *
         * @param from the account number
         * @return this builder
         * @throws IllegalArgumentException when the text is not an account number
         */
        public Builder from(String from) {
            return from(DomesticAccount.parse(from));
        }

        /**
         * Sets the recipient's account.
         *
         * @param to the account with its bank code
         * @return this builder
         */
        public Builder to(DomesticAccount to) {
            this.to = requireNonNull(to, "to");
            return this;
        }

        /**
         * Sets the recipient's account from its number, such as {@code 19-2000145399/0800}.
         *
         * @param to the account number with its bank code
         * @return this builder
         * @throws IllegalArgumentException when the text is not an account number
         */
        public Builder to(String to) {
            return to(DomesticAccount.parse(to));
        }

        /**
         * Sets the amount.
         *
         * @param amount the amount, in the currency of the payer's account
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
         * Sets the message the recipient sees.
         *
         * @param messageForRecipient up to 140 characters
         * @return this builder
         */
        public Builder messageForRecipient(String messageForRecipient) {
            this.messageForRecipient = requireNonNull(messageForRecipient, "messageForRecipient");
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
         * Sets the payment reason code.
         *
         * @param paymentReason a three-digit code from the list in Fio's documentation and import schema
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
         * @throws IllegalArgumentException when a part is invalid, as {@link DomesticPayment} describes
         */
        public DomesticPayment build() {
            return new DomesticPayment(
                    required(from, "payer's account"),
                    required(to, "recipient's account"),
                    required(amount, "amount"),
                    new PaymentSymbols(variableSymbol, constantSymbol, specificSymbol),
                    required(date, "date"),
                    messageForRecipient,
                    comment,
                    paymentReason,
                    type);
        }
    }
}
