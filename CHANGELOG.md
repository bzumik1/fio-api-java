# Changelog

All notable changes to this library are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the library follows
[semantic versioning](https://semver.org/): the public API is what `module-info.java` exports.

## [Unreleased]

### Added

- A client of the whole Fio banka REST API (version of 16 October 2025): transactions for a period or since the
  bank's bookmark, statements by number and the number of the last statement, the bookmark moves, card
  transactions of a merchant, every download format the bank offers (JSON, XML, CSV, GPC, HTML, OFX, MT940,
  CBA XML, PDF, STA) and payment import (domestic, euro and foreign payments built in Java or an ABO, Fio XML,
  pain.001 or pain.008 file).
- A fluent API: `fio.transactions().between(from, to).fetch()`, `fio.statements().latest()`,
  `fio.bookmark().moveAfterTransaction(id)`, `fio.merchant().between(from, to).fetch()`,
  `fio.payments().batch().domestic(p -> ...).submit()`.
- Typed values: `Money`, `DomesticAccount` with the Czech checksum, `ForeignAccount`, `StatementId`,
  `PaymentSymbols`, `TransactionType` and `CardTransactionType` with the labels the bank uses.
- A rate limiter that keeps the 30 seconds between requests the bank demands, shared by every thread using a
  client, and a bounded wait with a `FioRateLimitException` when the slot would come too late.
- Exceptions that tell what happened: authorisation, rate limit, locked history, too many transactions, transport
  (with whether the request may have reached the bank), malformed response. The token never appears in any of
  them.
- Java 17, a JPMS module `io.github.bzumik1.fio`, JSpecify nullness annotations, Jackson as the only runtime
  dependency.

[Unreleased]: https://github.com/bzumik1/fio-api-java/commits/main
