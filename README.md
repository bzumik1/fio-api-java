# fio-api

A Java client for the [Fio banka](https://www.fio.cz) REST API ("API Bankovnictví", documentation version 1.9
of 16 October 2025). It covers the whole API: account movements and official statements, typed or in any format
the bank offers, the server-side bookmark, a merchant's card transactions, and the submission of payment orders.

- Java 17 and newer, module `io.github.bzumik1.fio`, no framework. The only runtime dependency is Jackson 3
  (`tools.jackson.core:jackson-databind`); HTTP goes through `java.net.http.HttpClient`.
- A fluent API over immutable records, with [JSpecify](https://jspecify.dev) nullness annotations.
- The bank's one-request-per-30-seconds limit is handled by the client, which waits for the next slot instead of
  failing.
- The token never appears in a message, a `toString()` or an exception this library creates. Fio's API puts it in
  the URL path of every download, so it is in the request line your HTTP logging or proxy sees.
- Every line and branch is covered by tests, which run against an in-process stub of the bank.

```kotlin
dependencies {
    implementation("io.github.bzumik1:fio-api:1.0.0")
}
```

```xml
<dependency>
    <groupId>io.github.bzumik1</groupId>
    <artifactId>fio-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

```java
FioClient fio = FioClient.builder(FioToken.fromEnvironment("FIO_TOKEN")).build();

AccountStatement statement = fio.transactions().between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).fetch();
for (Transaction transaction : statement.transactions()) {
    if (transaction.amount().isPositive()) {
        System.out.println(transaction.date() + " " + transaction.amount()
                + " VS " + transaction.symbols().variable()
                + " from " + transaction.counterparty().name());
    }
}
```

### Movements

```java
AccountStatement january = fio.transactions().between(from, to).fetch();
AccountStatement sinceLastTime = fio.transactions().sinceBookmark().fetch();
Download csv = fio.transactions().between(from, to).download(TransactionFormat.CSV);
```

`between` downloads the movements dated within a period, both ends included. `sinceBookmark` downloads the
movements after the bank's bookmark and moves the bookmark to the newest movement it returns; an empty answer
leaves it where it was. `fetch()` returns typed records from the JSON format; `download(format)` returns the
bank's answer as it came, in one of `TransactionFormat`'s six formats (CSV, GPC, HTML, JSON, OFX, XML) as a
`Download` with its media type and bytes.

### Official statements

```java
StatementId latest = fio.statements().latest().id();
AccountStatement statement = fio.statements().latest().fetch();
Download pdf = fio.statements().number(2026, 3).download(StatementFormat.PDF);
```

A statement comes typed from its JSON format or raw in any of `StatementFormat`'s ten formats, which add PDF,
MT940 and the CBA and SBA XML formats to the six above.

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
        .euro(payment -> payment
                .from("2400222222/2010")
                .to("AT611904300234573201")
                .bic("ABAGATWWXXX")
                .amount(Money.of("99.90", "EUR"))
                .date(LocalDate.now(ZoneId.of("Europe/Prague")))
                .beneficiary(Beneficiary.named("Example GmbH")))
        .submit();

if (!result.isAccepted()) {
    result.messages().forEach(message -> System.err.println(message.text()));
}
```

A batch is built from `DomesticPayment`, `EuroPayment` (SEPA) and `ForeignPayment` orders, each with a builder
that checks what the bank checks (lengths, digits, allowed values) before anything is sent; `add(PaymentOrder)`
takes an order built elsewhere. The orders are serialised to Fio's XML import format and posted to the `import`
endpoint. `upload(PaymentFile)` posts a ready file in any format the bank imports (ABO, Fio XML, pain.001,
pain.008).

A submitted batch is only accepted: the bank processes it once an authorised person confirms it in internet
banking. `ImportResult` carries the bank's error code, the status (`OK`, `WARNING`, `ERROR`, `FATAL`), the batch
id, the sums per currency, and a message per rejected order. A batch the bank checked and refused comes back as a
result whose `isAccepted()` is false, not as an exception.

When a submission fails with `FioTransportException`, read `mayHaveReachedTheBank()` before trying again: a
timeout after the request went out means the batch may already be waiting for authorisation, and submitting it
again would pay twice. Check the pending batches in internet banking first. The same holds for
`sinceBookmark()`, where an answer lost on the way has already moved the bookmark.

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

A `FioClient` is immutable and safe to share between threads; requests through one client are serialised by its
rate limit. Build one client per token and share it, because two clients with the same token do not know about
each other's requests and the bank refuses the second one.

## The token

Each token belongs to one account and is created in Fio internet banking (Nastavení > API); it becomes usable
five minutes after it is authorised and lives at most 180 days unless it is renewed. A "Sledování účtu" token is
enough for every download; submitting payments needs a token allowed to do so.

`FioToken` never shows its value: `toString()` prints `FioToken[***]`, and every message, URL, response body and
cause the client puts into an exception has the token replaced by `***`. The token still travels in the URL path
of every download, by Fio's design, so the client refuses a `baseUri` that is not `https` (except on localhost). `FioToken.of(value)` strips surrounding
whitespace, so a token read from a file with a trailing newline works; `FioToken.fromEnvironment("FIO_TOKEN")`
reads it from an environment variable.

## Limits of the bank, and how the client handles them

- **One request per token per 30 seconds**, whatever the endpoint or format. The client keeps the interval between
  its own requests and, when the bank still answers HTTP 409, waits until the bank's retry time; only when the
  wait would exceed `RateLimit.maximumWait()` does the request fail with `FioRateLimitException`, whose
  `retryAfter()` says when the next request may go.
- **90 days of history.** Movements older than 90 days (counted from today, Prague time) are available only for 10
  minutes after the account holder unlocks the full history for the token in internet banking. Without it the bank
  answers HTTP 422, which becomes `FioHistoryLockedException`.
- **50 000 movements per download.** A larger answer is refused with HTTP 413, which becomes
  `FioTooManyTransactionsException`; ask for a shorter period or move the bookmark forward.
- A wrong or expired token is HTTP 500 from the bank, which becomes `FioAuthorizationException`.
- A request that cannot be sent within the `RateLimit` is `FioRateLimitException` with an empty `statusCode()`.

## Errors

Every failure is a `FioException`, an unchecked exception with the HTTP status and the bank's response body
(token masked). The hierarchy is sealed, so a `switch` over it is exhaustive on Java 21 and newer:
`FioRateLimitException`, `FioAuthorizationException`, `FioHistoryLockedException`,
`FioTooManyTransactionsException`, `FioTransportException` (no answer: connection, timeout, interruption; its
`mayHaveReachedTheBank()` says whether the request may have gone out) and the plain `FioException` for everything
else, including an answer the client does not understand.

## Compatibility

The public API is what `module-info.java` exports, under semantic versioning, with two things to know: the
interfaces nested in `FioClient` may gain methods in a minor release, so implement them outside this library (as a
test fake, say) only at your own risk; and the enums `TransactionType`, `CardTransactionType` and the format enums
may gain constants, so a `switch` over them needs a `default`. The records that carry bank data (`Transaction`,
`AccountInfo`, `CardTransaction`, `ImportResult`) may gain components in a minor release when Fio adds a field;
their accessors stay, but code that calls their canonical constructor or deconstructs them with a record pattern
has to be revisited.

Bad input fails before anything is sent: a malformed value (an account number that fails the bank's checksum, a
symbol that is not digits, a period that ends before it starts) is an `IllegalArgumentException` that names the
problem, a payment builder with a required part missing is an `IllegalStateException`, and a `null` argument is a
`NullPointerException`.

## The model

- `AccountStatement(info, transactions)`: one download of movements. `AccountInfo` holds the account
  (`DomesticAccount`), IBAN, BIC, currency, opening and closing balance (`Money`), the period, the statement
  number and the ids of the first, last and bookmarked movement.
- `Transaction`: one movement. `id`, `date`, `amount` (`Money`, negative for outgoing), `type`
  (`TransactionType`, one of the 43 distinct types in Fio's list, which repeats two of its 45 entries, or `OTHER`) with the bank's `typeLabel`, `counterparty`
  (account, name, bank name, BIC), `symbols` (variable, constant, specific), `userIdentification`, `executedBy`,
  `messageForRecipient`, `instructionId`, `detail`, `comment`, `payerReference`, and `columns`, every column the
  bank sent by its number, for anything new.
- `Money(amount, currency)`, which keeps the bank's scale but compares by value (`1.0 CZK` equals `1.00 CZK`),
  `DomesticAccount(prefix, number, bankCode)` with `toIban()` and `hasValidChecksum()`, `ForeignAccount(identifier)`,
  `PaymentSymbols`, `Counterparty`, `StatementId(year, number)`.
- `MerchantStatement(account, iban, bic, currency, dateStart, dateEnd, transactions)` with `CardTransaction`
  records mapped from the documented XML fields, amounts and fees as `Money` in the account's currency.
- `Download(mediaType, charset, content)` for a raw download; `asText()` decodes with the charset the bank named
  or, when it named none, the one Fio documents for the format (Windows-1250 for GPC, UTF-8 otherwise).

A counterparty account that looks Czech (digits, optional prefix, a 4-digit bank code) is a `DomesticAccount`;
anything else, such as an IBAN, is a `ForeignAccount`. A variable symbol of `0`, which the bank sends for some
movements, is kept as `"0"`.

## Documentation

`docs/` holds Fio's own documentation of the API in the version this library implements. What the library found
out by talking to the bank, beyond the documentation, is in the Javadoc of the relevant types.

## Building

```
./gradlew build                 # compile, test on Java 17, enforce 100 % coverage, javadoc
./gradlew build -PtestJdk=25    # the same, with the tests running on Java 25
```

Java 17 is downloaded by Gradle's toolchain; any JDK that runs Gradle 9 is enough. Releases are described in
[docs/releasing.md](docs/releasing.md).

## License

[MIT](LICENSE).
