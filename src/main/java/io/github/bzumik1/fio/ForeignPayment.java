package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.github.bzumik1.fio.PaymentText.optional;
import static io.github.bzumik1.fio.PaymentText.payerAccount;
import static io.github.bzumik1.fio.PaymentText.positive;
import static io.github.bzumik1.fio.PaymentText.required;
import static java.util.Objects.requireNonNull;

/**
 * A payment abroad outside SEPA.
 *
 * @param from the payer's account
 * @param to the recipient's account, an IBAN or a local account number of up to 34 characters
 * @param bic the BIC of the recipient's bank
 * @param amount the amount, in the currency of the payer's account
 * @param date the day the payment is due
 * @param comment the payer's own label, up to 140 characters
 * @param beneficiary the holder of the recipient's account, with the full address
 * @param remittanceInfo one to four lines of 35 characters for the recipient
 * @param charges who pays the fees
 * @param paymentReason the payment reason code from Fio's list, such as {@code 348}
 */
public record ForeignPayment(
        DomesticAccount from,
        ForeignAccount to,
        String bic,
        Money amount,
        LocalDate date,
        @Nullable String comment,
        Beneficiary beneficiary,
        List<String> remittanceInfo,
        Charges charges,
        String paymentReason) implements PaymentOrder {

    private static final int REMITTANCE_LINES = 4;

    /**
     * Validates the components.
     *
     * @param from the payer's account
     * @param to the recipient's account
     * @param bic the BIC
     * @param amount the amount
     * @param date the due date
     * @param comment the comment
     * @param beneficiary the beneficiary
     * @param remittanceInfo the lines for the recipient
     * @param charges who pays the fees
     * @param paymentReason the payment reason
     * @throws IllegalArgumentException when the payer's account is not a Fio account, the amount is not positive
     *         or has more than two decimal places or sixteen digits before them, the BIC is malformed, the due
     *         date's year is not four digits, a text is too long, blank or holds a character XML cannot carry, the
     *         beneficiary's address is incomplete, there is no remittance line or more than four, or the payment
     *         reason is not a code of Fio's list
     */
    public ForeignPayment {
        from = payerAccount(from);
        to = new ForeignAccount(required(requireNonNull(to, "to").identifier().replace(" ", ""), 34, "recipient's account"));
        bic = PaymentText.bic(bic);
        amount = positive(amount);
        date = PaymentText.dueDate(date);
        comment = optional(comment, 140, "comment");
        requireNonNull(beneficiary, "beneficiary");
        if (!beneficiary.hasFullAddress()) {
            throw new IllegalArgumentException("A payment outside SEPA needs the beneficiary's street, city and country");
        }
        remittanceInfo = EuroPayment.remittanceLines(remittanceInfo, REMITTANCE_LINES);
        if (remittanceInfo.isEmpty()) {
            throw new IllegalArgumentException("A payment outside SEPA needs at least one remittance line");
        }
        requireNonNull(charges, "charges");
        paymentReason = requireNonNull(PaymentText.paymentReason(requireNonNull(paymentReason, "paymentReason")));
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
     * Who pays the fees of a payment abroad, as in SWIFT's details of charges.
     */
    public enum Charges {

        /** The payer pays every fee (OUR), code 470501. */
        PAYER("470501"),
        /** The recipient pays every fee (BEN), code 470502. */
        BENEFICIARY("470502"),
        /** Each side pays its own bank (SHA), code 470503. */
        SHARED("470503");

        private final String code;

        Charges(String code) {
            this.code = code;
        }

        /**
         * Returns the code Fio uses for the option.
         *
         * @return the code
         */
        public String code() {
            return code;
        }
    }

    /**
     * Builds a {@link ForeignPayment}. Everything but the comment and the remittance lines after the first is
     * required; the charges are {@link Charges#SHARED} unless set.
     */
    public static final class Builder {

        private @Nullable DomesticAccount from;
        private @Nullable ForeignAccount to;
        private @Nullable String bic;
        private @Nullable Money amount;
        private @Nullable LocalDate date;
        private @Nullable String comment;
        private @Nullable Beneficiary beneficiary;
        private final List<String> remittanceInfo = new ArrayList<>();
        private Charges charges = Charges.SHARED;
        private @Nullable String paymentReason;

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
         * Sets the recipient's account.
         *
         * @param account an IBAN or a local account number
         * @return this builder
         */
        public Builder to(String account) {
            return to(new ForeignAccount(account));
        }

        /**
         * Sets the recipient's account.
         *
         * @param account the account, by IBAN or a local account number
         * @return this builder
         */
        public Builder to(ForeignAccount account) {
            this.to = requireNonNull(account, "account");
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
         * @param amount the amount, in the currency of the payer's account
         * @return this builder
         */
        public Builder amount(Money amount) {
            this.amount = requireNonNull(amount, "amount");
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
         * Sets the holder of the recipient's account, with the full address.
         *
         * @param beneficiary the beneficiary
         * @return this builder
         */
        public Builder beneficiary(Beneficiary beneficiary) {
            this.beneficiary = requireNonNull(beneficiary, "beneficiary");
            return this;
        }

        /**
         * Adds a line of information for the recipient.
         *
         * @param line up to 35 characters; one to four lines
         * @return this builder
         */
        public Builder remittanceInfo(String line) {
            remittanceInfo.add(requireNonNull(line, "line"));
            return this;
        }

        /**
         * Sets who pays the fees.
         *
         * @param charges the option, {@link Charges#SHARED} by default
         * @return this builder
         */
        public Builder charges(Charges charges) {
            this.charges = requireNonNull(charges, "charges");
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
         * Builds the payment.
         *
         * @return the payment
         * @throws IllegalStateException when a required part is missing
         * @throws IllegalArgumentException when a part is invalid
         */
        public ForeignPayment build() {
            return new ForeignPayment(
                    required(from, "payer's account"),
                    required(to, "recipient's account"),
                    required(bic, "BIC"),
                    required(amount, "amount"),
                    required(date, "date"),
                    comment,
                    required(beneficiary, "beneficiary"),
                    remittanceInfo,
                    charges,
                    required(paymentReason, "payment reason"));
        }
    }
}
