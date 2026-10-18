/**
 * A client of the Fio banka REST API. Start with {@link io.github.bzumik1.fio.FioClient}.
 *
 * <p>Nothing in this package accepts or returns {@code null} unless marked {@link org.jspecify.annotations.Nullable};
 * the exceptions alone return {@link java.util.Optional} for what they may lack, as they are read far from where
 * they are thrown.
 *
 * <p>The package follows semantic versioning. A minor release may change only this:
 * <ul>
 * <li>{@link io.github.bzumik1.fio.FioClient} and the interfaces nested in it may gain methods as Fio extends its
 * API, so implementing them outside this library, for a test fake, say, is not covered.</li>
 * <li>The enums that follow Fio's lists may gain constants: {@link io.github.bzumik1.fio.TransactionType},
 * {@link io.github.bzumik1.fio.CardTransactionType}, {@link io.github.bzumik1.fio.TransactionFormat},
 * {@link io.github.bzumik1.fio.StatementFormat}, {@link io.github.bzumik1.fio.MerchantFormat},
 * {@link io.github.bzumik1.fio.DomesticPayment.Type}, {@link io.github.bzumik1.fio.EuroPayment.Type},
 * {@link io.github.bzumik1.fio.ForeignPayment.Charges}, {@link io.github.bzumik1.fio.PaymentFileType} and
 * {@link io.github.bzumik1.fio.ImportResult.Status}. A {@code switch} over any enum of this package needs a
 * {@code default} branch.</li>
 * <li>These records may gain components when Fio adds a field: {@link io.github.bzumik1.fio.Transaction},
 * {@link io.github.bzumik1.fio.AccountInfo}, {@link io.github.bzumik1.fio.AccountStatement},
 * {@link io.github.bzumik1.fio.CardTransaction}, {@link io.github.bzumik1.fio.MerchantStatement},
 * {@link io.github.bzumik1.fio.ImportResult}, {@link io.github.bzumik1.fio.ImportResult.Sum},
 * {@link io.github.bzumik1.fio.ImportResult.Message}, {@link io.github.bzumik1.fio.TransactionColumn},
 * {@link io.github.bzumik1.fio.Counterparty}, {@link io.github.bzumik1.fio.Download} and the three payment orders,
 * {@link io.github.bzumik1.fio.DomesticPayment}, {@link io.github.bzumik1.fio.EuroPayment} and
 * {@link io.github.bzumik1.fio.ForeignPayment}. Their accessors are stable; their canonical constructors and
 * record patterns are not, so construct the payment orders with their builders.</li>
 * <li>{@link io.github.bzumik1.fio.FioException} may gain subtypes for failures that are a plain
 * {@code FioException} until then. The class itself is thrown too, so a {@code switch} over it names each subtype
 * and ends with {@code case FioException e} for the rest, which still catches a new subtype; a {@code catch} of
 * the plain class does too.</li>
 * </ul>
 *
 * <p>The sealed hierarchies {@link io.github.bzumik1.fio.BankAccount}, {@link io.github.bzumik1.fio.PaymentOrder}
 * and {@link io.github.bzumik1.fio.DownloadFormat} gain permitted subtypes only in a major release, so an
 * exhaustive {@code switch} over them is safe. Everything else is stable in full, among it the value types
 * {@link io.github.bzumik1.fio.Money},
 * {@link io.github.bzumik1.fio.DomesticAccount}, {@link io.github.bzumik1.fio.ForeignAccount},
 * {@link io.github.bzumik1.fio.StatementId}, {@link io.github.bzumik1.fio.PaymentSymbols},
 * {@link io.github.bzumik1.fio.Beneficiary}, {@link io.github.bzumik1.fio.PaymentBatch},
 * {@link io.github.bzumik1.fio.PaymentFile}, {@link io.github.bzumik1.fio.FioToken} and
 * {@link io.github.bzumik1.fio.RateLimit}.
 */
@NullMarked
package io.github.bzumik1.fio;

import org.jspecify.annotations.NullMarked;
