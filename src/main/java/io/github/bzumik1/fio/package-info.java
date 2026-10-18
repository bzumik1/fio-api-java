/**
 * A client of the Fio banka REST API. Start with {@link io.github.bzumik1.fio.FioClient}.
 *
 * <p>Nothing in this package accepts or returns {@code null} unless marked {@link org.jspecify.annotations.Nullable};
 * the exceptions alone return {@link java.util.Optional} for what they may lack, as they are read far from where
 * they are thrown.
 *
 * <p>The records that carry what the bank sent ({@link io.github.bzumik1.fio.Transaction},
 * {@link io.github.bzumik1.fio.AccountInfo}, {@link io.github.bzumik1.fio.CardTransaction},
 * {@link io.github.bzumik1.fio.MerchantStatement}, {@link io.github.bzumik1.fio.ImportResult}) may gain components
 * in a minor release when Fio adds a field. Their accessors are stable; their canonical constructors and record
 * patterns are not covered by semantic versioning. The small value types ({@link io.github.bzumik1.fio.Money},
 * {@link io.github.bzumik1.fio.DomesticAccount}, {@link io.github.bzumik1.fio.StatementId} and the like) and the
 * payment orders are stable in full.
 */
@NullMarked
package io.github.bzumik1.fio;

import org.jspecify.annotations.NullMarked;
