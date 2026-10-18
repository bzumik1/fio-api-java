# fio-api

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bzumik1/fio-api)](https://central.sonatype.com/artifact/io.github.bzumik1/fio-api)
[![javadoc](https://javadoc.io/badge2/io.github.bzumik1/fio-api/javadoc.svg)](https://javadoc.io/doc/io.github.bzumik1/fio-api)
[![Build](https://github.com/bzumik1/fio-api-java/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/bzumik1/fio-api-java/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A Java client for the [Fio banka](https://www.fio.cz) REST API ("API Bankovnictví", documentation version 1.9
of 16 October 2025). It covers the whole API: account movements and official statements, typed or in any format
the bank offers, the server-side bookmark, a merchant's card transactions, and the submission of payment orders.

- Java 17 and newer, module `io.github.bzumik1.fio`, no framework. Two runtime dependencies: Jackson 3
  (`tools.jackson.core:jackson-databind`) and the JSpecify annotations (`org.jspecify:jspecify`, a few kilobytes
  of annotations); HTTP goes through `java.net.http.HttpClient`.
- A fluent API over immutable records, with [JSpecify](https://jspecify.dev) nullness annotations.
- The bank's one-request-per-30-seconds limit is handled by the client, which waits for the next slot instead of
  failing.
- The token never appears in a message, a `toString()` or an exception this library creates. Fio's API puts it in
  the URL path of every download, so it is in the request line your HTTP logging or proxy sees.
- Every line and branch is covered by tests, which run against an in-process stub of the bank.

## Installation

Gradle, Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.bzumik1:fio-api:1.0.0")
}
```

Gradle, Groovy DSL:

```groovy
dependencies {
    implementation 'io.github.bzumik1:fio-api:1.0.0'
}
```

Maven:

```xml
<dependency>
    <groupId>io.github.bzumik1</groupId>
    <artifactId>fio-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

A modular application adds the module to its `module-info.java`:

```
requires io.github.bzumik1.fio;
```

## Usage

```java
FioClient fio = FioClient.builder(FioToken.fromEnvironment("FIO_TOKEN")).build();

LocalDate today = LocalDate.now(ZoneId.of("Europe/Prague"));
AccountStatement statement = fio.transactions().between(today.minusDays(30), today).fetch();
for (Transaction transaction : statement.transactions()) {
    if (transaction.isIncoming()) {
        System.out.println(transaction.date() + " " + transaction.amount()
                + " VS " + transaction.symbols().variable()
                + " from " + transaction.counterparty().name());
    }
}
```

### Movements

```java
LocalDate today = LocalDate.now(ZoneId.of("Europe/Prague"));
AccountStatement lastMonth = fio.transactions().between(today.minusDays(30), today).fetch();
AccountStatement sinceLastTime = fio.transactions().sinceBookmark().fetch();
Download csv = fio.transactions().between(today.minusDays(30), today).download(TransactionFormat.CSV);
```

`between` downloads the movements dated within a period, both ends included. `sinceBookmark` downloads the
movements after the bank's bookmark and moves the bookmark to the newest movement it returns; an empty answer
leaves it where it was. `fetch()` returns typed records from the JSON format; `download(format)` returns the
bank's answer as it came, in one of `TransactionFormat`'s six formats (CSV, GPC, HTML, JSON, OFX, XML) as a
`Download` with its media type and bytes.

### Official statements

```java
FioClient.StatementRequest latest = fio.statements().latest();
StatementId number = latest.statementId();
AccountStatement statement = latest.fetch();
Download pdf = latest.download(StatementFormat.PDF);
```

`fio.statements().number(2026, 3)` chooses a statement by its number; one covering days more than 90 days back
needs the full history unlocked, as described below.

A statement comes typed from its JSON format or raw in any of `StatementFormat`'s ten formats, which add PDF,
MT940 and the CBA and SBA XML formats to the six above. `latest()` asks the bank for the number once, on the
first call that needs it, and keeps it on the request, so hold on to the request rather than calling `latest()`
again; every call to the bank is 30 seconds apart.

### The bookmark

```java
fio.bookmark().moveAfterTransaction(lastProcessedTransactionId);
fio.bookmark().moveToDate(firstDayThatFailed);
```

Both move the bookmark `sinceBookmark()` continues from: behind a movement, or to the first movement of a day.

### Card transactions of a merchant

```java
MerchantStatement cards = fio.merchant().between(from, to).fetch();
Download csv = fio.merchant().between(from, to).download(MerchantFormat.CSV);
```

### Payment orders

```java
ImportResult result = fio.payments().batch()
        .domestic(payment -> payment
                .from("2400222222/2010")
                .to("19-2000145399/0800")
                .amount(Money.czk("1250.00"))
                .date(LocalDate.now(ZoneId.of("Europe/Prague")))
                .variableSymbol("20260001")
                .messageForRecipient("Invoice 2026-0001"))
        .domestic(payment -> payment
                .from("2400222222/2010")
                .to("2001234561/0100")
                .amount(Money.czk("380.50"))
                .date(LocalDate.now(ZoneId.of("Europe/Prague")))
                .variableSymbol("20260002"))
        .submit();

if (!result.isAccepted()) {
    result.messages().forEach(message -> System.err.println(message.text()));
}
```

A token belongs to one account, and every order in a batch is from that account and in its currency; a batch
with orders from two accounts, or in two currencies, is refused before anything is sent. From a euro account, with its own token, the
same batch takes SEPA payments:

```java
FioClient fio = FioClient.builder(FioToken.fromEnvironment("FIO_EUR_TOKEN")).build();

ImportResult result = fio.payments().batch()
        .euro(payment -> payment
                .from("2400333333/2010")
                .to("AT611904300234573201")
                .bic("ABAGATWWXXX")
                .amount(Money.of("99.90", "EUR"))
                .date(LocalDate.now(ZoneId.of("Europe/Prague")))
                .beneficiary(Beneficiary.named("Example GmbH")))
        .submit();
```

A batch is built from `DomesticPayment`, `EuroPayment` (SEPA) and `ForeignPayment` orders, each with a builder
that checks what the bank checks (lengths, digits, allowed values) before anything is sent; `add(PaymentOrder)`
takes an order built elsewhere. The orders are serialised to Fio's XML import format and posted to the `import`
endpoint. `upload(PaymentFile)` posts a ready file in any format the bank imports (ABO, Fio XML, pain.001,
pain.008).

A submitted batch is only accepted: the bank processes it once an authorised person confirms it in internet
banking. `ImportResult` carries the bank's error code, the status (`OK`, `WARNING`, `ERROR`, `FATAL`), the
`batchId` the bank gave the batch (not the `instructionId` of a single movement), the sums per currency, and a
message per rejected order. A batch the bank checked and refused comes back as a
result whose `isAccepted()` is false, not as an exception.

When a submission fails, read the exception's `mayHaveTakenEffect()` before trying again: when it is true (a
timeout after the request went out, an answer the client could not read or one too long to read), the batch may
already be waiting for authorisation, and submitting it again would pay twice, so check the pending batches in
internet banking first. The same holds for `sinceBookmark()`, which may already have moved the bookmark: move it
back to the last stored movement before downloading again.

### Configuration

```java
FioClient fio = FioClient.builder(token)
        .rateLimit(RateLimit.waitUpTo(Duration.ofSeconds(45)))
        .requestTimeout(Duration.ofSeconds(30))
        .messageLocale(Locale.forLanguageTag("cs"))
        .httpClient(myHttpClient)
        .build();
```

| Option | Default | Meaning |
|---|---|---|
| `rateLimit` | wait up to 2 minutes | How long a request may wait for the bank's 30-second slot; `RateLimit.failFast()` never waits. |
| `requestTimeout` | 2 minutes | Timeout of one HTTP request. |
| `messageLocale` | English | Language of the bank's own messages (Czech, Slovak or English). |
| `httpClient` | 10-second connect timeout | Your own `java.net.http.HttpClient`, for a proxy or a custom SSL context. |
| `baseUri` | `https://fioapi.fio.cz/v1/rest` | Where the bank is; point it at a stub in tests. |
| `clock` | the system clock | The clock the rate limiting and `FioRateLimitException.retryAfter()` use. |
| `maximumResponseSize` | 256 MiB | The most bytes of one answer the client reads; a longer answer fails instead of filling the heap. |

A `FioClient` is immutable and safe to share between threads; requests through one client are serialised by its
rate limit. Build one client per token and share it, because two clients with the same token do not know about
each other's requests and the bank refuses the second one.

## The token

Each token belongs to one account and is created in Fio internet banking (Nastavení > API); it becomes usable
five minutes after it is authorised and lives at most 180 days unless it is renewed. A "Sledování účtu" token is
enough for every download; submitting payments needs a token allowed to do so.

`FioToken` never shows its value: `toString()` prints `FioToken[***]`, and every message, URL, response body and
cause the client puts into an exception has the token replaced by `***`. The token still travels in the URL path
of every download, by Fio's design, so the client refuses a `baseUri` that is not `https` (plain `http` only to
the literal loopback addresses `127.0.0.1` and `[::1]`, or anywhere after `allowPlainHttp()`).
`FioToken.of(value)` strips surrounding whitespace, so a token read from a file with a trailing newline works;
`FioToken.fromEnvironment("FIO_TOKEN")` reads it from an environment variable.

## Limits of the bank, and how the client handles them

- **One request per token per 30 seconds**, whatever the endpoint or format. The client keeps the interval between
  its own requests and, when the bank still answers HTTP 409, waits until the bank's retry time; only when the
  next slot would come later than `RateLimit.maximumWait()` after the call started (its HTTP exchanges included)
  does the request fail with `FioRateLimitException`, whose `retryAfter()` says when the next request may go.
- **90 days of history.** Data older than 90 days (counted from today, Prague time), movements, statements and
  card transactions alike, is available only for 10 minutes after the account holder unlocks the full history for
  the token in internet banking. Without it the bank answers HTTP 422, which becomes `FioHistoryLockedException`.
- **50 000 movements per download.** A larger answer is refused with HTTP 413, which becomes
  `FioTooManyTransactionsException`; ask for a shorter period or move the bookmark forward.
- A wrong or expired token is HTTP 500 from the bank, which becomes `FioAuthorizationException`; so is a bank
  failure, so check that the bank is up before replacing a token that worked a moment ago.
- A request that cannot be sent within the `RateLimit` is `FioRateLimitException` with an empty `statusCode()`.
- An answer longer than 256 MiB is not read and fails with `FioResponseTooLargeException`. The largest answer the
  bank documents, 50 000 movements as JSON with every field at its longest, is about 254 MiB; a client that only
  downloads short periods can lower the size with the builder's `maximumResponseSize`.

## Errors

Every failure is a `FioException`, an unchecked exception with the HTTP status and the first 16 Ki characters
(16 384) of the bank's response body (token masked); a value from the body that the message quotes is cut to its
first 200 characters. The hierarchy is sealed, and the plain `FioException` is thrown too, so on Java 21 and
newer a `switch` can name each subtype and ends with `case FioException e` for the rest:
`FioRateLimitException`, `FioAuthorizationException`, `FioHistoryLockedException` (its `earliestAvailableDay()`
reads the day the bank's answer names, when it names one), `FioTooManyTransactionsException`,
`FioMalformedResponseException` (a success status with a body the client does not understand),
`FioTransportException` (no answer: connection, TLS handshake, timeout, interruption; its
`mayHaveReachedTheBank()` says whether the request may have gone out), `FioResponseTooLargeException` (an answer
longer than the client reads, with its `limit()`) and the plain `FioException` for any other status.

When `mayHaveTakenEffect()` is true, the bank may have acted on the request: after a payment submission check the
pending batches in internet banking before submitting again, and after `sinceBookmark()` move the bookmark back
before downloading again. A download by period or number can simply be asked again.

## Compatibility

The public API is what `module-info.java` exports, under semantic versioning. A minor release may change only
this:

- `FioClient` and the interfaces nested in it may gain methods as Fio extends its API, so implement them outside
  this library (as a test fake, say) only at your own risk.
- The enums that follow Fio's lists may gain constants: `TransactionType`, `CardTransactionType`,
  `TransactionFormat`, `StatementFormat`, `MerchantFormat`, `DomesticPayment.Type`, `EuroPayment.Type`,
  `ForeignPayment.Charges`, `PaymentFileType` and `ImportResult.Status`. Fio's own list of payment types has
  dropped a type and gained instant payments before. A `switch` over any enum of the library needs a `default`.
- These records may gain components when Fio adds a field: `Transaction`, `AccountInfo`, `AccountStatement`,
  `CardTransaction`, `MerchantStatement`, `ImportResult`, `ImportResult.Sum`, `ImportResult.Message`,
  `TransactionColumn`, `Counterparty`, `Download` and the three payment orders, `DomesticPayment`, `EuroPayment`
  and `ForeignPayment`. Their accessors stay, but code that calls their canonical constructor or deconstructs
  them with a record pattern has to be revisited; construct the payment orders with their builders.
- `FioException` may gain subtypes for failures that are a plain `FioException` until then. The class itself is
  thrown too, so a `switch` over it names each subtype and ends with `case FioException e` for the rest, which
  still catches a new subtype; a `catch` of the plain class does too.

The sealed hierarchies `BankAccount`, `PaymentOrder` and `DownloadFormat` gain permitted subtypes only in a major
release, so an exhaustive `switch` over them is safe. Everything else is stable in full, among it
the value types `Money`, `DomesticAccount`, `ForeignAccount`, `StatementId`, `PaymentSymbols`, `Beneficiary`,
`PaymentBatch`, `PaymentFile`, `FioToken` and `RateLimit`.

Bad input fails before anything is sent: a malformed value (an account number that fails the bank's checksum, a
symbol that is not digits, a period that ends before it starts) is an `IllegalArgumentException` that names the
problem, a payment builder with a required part missing is an `IllegalStateException`, and a `null` argument is a
`NullPointerException`.

The client refuses a base URI other than `https`, because the token travels in the request path; plain `http` is
accepted for the loopback addresses `127.0.0.1` and `[::1]`, where test stubs live, or anywhere after the
builder's `allowPlainHttp()`, called before or after `baseUri(...)`: `build()` checks the two together. A custom
`HttpClient` has to keep the JDK's default of not following redirects.

## The model

- `AccountStatement(info, transactions)`: one download of movements. `AccountInfo` holds the account
  (`DomesticAccount`), IBAN, BIC, currency, opening and closing balance (`Money`), the period, the statement
  number and the ids of the first, last and bookmarked movement.
- `Transaction`: one movement. `id`, `date`, `amount` (`Money`, negative for outgoing), `type`
  (`TransactionType`, one of the 43 distinct types in Fio's list, which repeats two of its 45 entries, or
  `OTHER`) with the bank's `typeLabel`, `counterparty` (account, name, bank name, BIC), `symbols` (variable, constant, specific), `userIdentification`, `executedBy`,
  `messageForRecipient`, `instructionId`, `detail`, `comment`, `payerReference`, and `columns`, every column the
  bank sent by its number, for anything new.
- `Money(amount, currency)`, which keeps the bank's scale but compares by value (`1.0 CZK` equals `1.00 CZK`),
  `DomesticAccount(prefix, number, bankCode)` with `toIban()` and `hasValidChecksum()`, `ForeignAccount(identifier)`,
  `PaymentSymbols`, `Counterparty`, `StatementId(year, number)`.
- `MerchantStatement(account, iban, bic, currency, dateStart, dateEnd, transactions)` with `CardTransaction`
  records mapped from the documented XML fields, amounts and fees as `Money` in the account's currency.
- `Download(mediaType, charset, content)` for a raw download; `asText()` decodes with the charset the bank named
  or, when it named none, the one Fio documents for the format (Windows-1250 for GPC, UTF-8 otherwise).

A counterparty account that looks Czech (digits, optional prefix, a 4-digit bank code when the bank sends one) is
a `DomesticAccount`; anything else, such as an IBAN, is a `ForeignAccount`. A variable symbol of `0`, which the
bank sends for some movements, is kept as `"0"`.

## Documentation

`docs/` holds Fio's own documentation of the API in the version this library implements. What the library found
out by talking to the bank, beyond the documentation, is in the Javadoc of the relevant types.

## Building

```
./gradlew build                      # compile, test on Java 17, enforce 100 % coverage, javadoc, module smoke test
./gradlew build -PtestJdk=25         # the same, with the tests running on Java 25
./gradlew build publishToMavenLocal  # also publish the snapshot into ~/.m2, as the pull request check does
```

Java 17 is downloaded by Gradle's toolchain; any JDK that runs Gradle 9 is enough. Dependency and plugin
versions live in `gradle/libs.versions.toml`. How to contribute is in [CONTRIBUTING.md](CONTRIBUTING.md),
releases are described in [docs/releasing.md](docs/releasing.md).

## License

[MIT](LICENSE). Fio banka's documentation in `docs/` and the XML schemas in `src/test/resources/schema/` are
Fio's own and are not covered by it; see [docs/README.md](docs/README.md).
