# Changelog

All notable changes to this library are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the library follows
[semantic versioning](https://semver.org/): the public API is what `module-info.java` exports.

## [Unreleased]

### Added

- A client of the whole Fio banka REST API (version of 16 October 2025): transactions for a period or since the
  bank's bookmark, statements by number and the number of the last statement, the bookmark moves, card
  transactions of a merchant, every download format the bank offers (JSON, XML, CSV, GPC, HTML, OFX, MT940
  as `sta`, CBA XML, SBA XML, PDF) and payment import (domestic, euro and foreign payments built in Java or an
  ABO, Fio XML, pain.001 or pain.008 file), answered with an `ImportResult` that holds the status, the
  `batchId` the bank gave the batch, the sums and the bank's messages per order.
- A fluent API: `fio.transactions().between(from, to).fetch()`, `fio.statements().latest()`,
  `fio.bookmark().moveAfterTransaction(id)`, `fio.merchant().between(from, to).fetch()`,
  `fio.payments().batch().domestic(p -> ...).submit()`.
- Typed values: `Money`, `DomesticAccount` with the Czech checksum, `ForeignAccount`, `StatementId`,
  `PaymentSymbols`, `TransactionType` and `CardTransactionType` with the labels the bank uses.
- A rate limiter that keeps the 30 seconds between requests the bank demands, shared by every thread using a
  client, and a bounded wait with a `FioRateLimitException` when the slot would come too late.
- Exceptions that tell what happened: authorisation, rate limit, locked history (with the earliest available day
  when the bank names it), too many transactions, malformed response, an answer too large to read and transport
  (with whether the request may have reached the bank, which a failed TLS handshake certainly did not), each
  with `mayHaveTakenEffect()`, which says whether a submitted batch may have been accepted or the bookmark
  moved. The token never appears in any of them, not even cut short, a response body is kept to its first
  16 Ki characters and a value quoted from it to its first 200.
- Payment batches are checked before sending: one account, one currency, at most 2 MiB of Fio XML.
- Bounded response numbers: a decimal the bank sends has at most 40 digits before the point and 1000 after it,
  so a hostile answer cannot make the client build a huge `BigDecimal`.
- Safety against a misbehaving network: an answer longer than 256 MiB (the builder's `maximumResponseSize`) is
  not read, the base URI has to be `https` with a host and without query or fragment (plain `http` only to a
  loopback address or after `allowPlainHttp()`), and a custom `HttpClient` must not follow redirects.
- Java 17, a JPMS module `io.github.bzumik1.fio`, JSpecify nullness annotations; the runtime dependencies are
  Jackson and the JSpecify annotations.

[Unreleased]: https://github.com/bzumik1/fio-api-java/commits/main
