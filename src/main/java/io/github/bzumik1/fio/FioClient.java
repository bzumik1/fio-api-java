package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A client of the Fio banka REST API ("API Bankovnictví") for one account, identified by its {@link FioToken}.
 *
 * <p>The calls are grouped by what they work with: {@link #transactions()} downloads account movements,
 * {@link #statements()} the official statements, {@link #merchant()} the card transactions of a merchant,
 * {@link #bookmark()} moves the server-side bookmark of the last downloaded movement and {@link #payments()}
 * submits payment orders. A download is chosen first and then fetched as typed records or downloaded raw in one of
 * the formats Fio offers:
 *
 * <pre>{@code
 * FioClient fio = FioClient.builder(FioToken.fromEnvironment("FIO_TOKEN")).build();
 *
 * AccountStatement week = fio.transactions().between(LocalDate.now().minusDays(7), LocalDate.now()).fetch();
 * Download pdf = fio.statements().latest().download(StatementFormat.PDF);
 * }</pre>
 *
 * <p>Fio accepts one request per token every {@link #MINIMUM_REQUEST_INTERVAL 30 seconds}. The client keeps to
 * that limit itself according to the {@link RateLimit} chosen in the {@link Builder}: by default it waits for the
 * next slot, and a request the bank refuses with HTTP 409 is repeated once the slot is free.
 *
 * <p>A client is immutable and safe to share between threads; build one per token and share it, since two clients
 * with the same token cannot keep the interval between each other's requests. Every failed request ends in a
 * {@link FioException}; a request that cannot be sent because its arguments are invalid ends in an
 * {@link IllegalArgumentException} before anything reaches the bank.
 *
 * <p>This interface and the interfaces nested in it may gain methods in a minor release, as Fio extends its API.
 * Implementing them outside this library, for a test fake, say, is therefore not covered by semantic versioning.
 */
public interface FioClient {

    /** The address of the production API. */
    URI DEFAULT_BASE_URI = URI.create("https://fioapi.fio.cz/v1/rest");

    /** The shortest interval Fio allows between two requests with the same token, whatever the endpoint. */
    Duration MINIMUM_REQUEST_INTERVAL = Duration.ofSeconds(30);

    /**
     * Returns the downloads of account movements.
     *
     * @return the movement downloads
     */
    Transactions transactions();

    /**
     * Returns the downloads of the official account statements.
     *
     * @return the statement downloads
     */
    Statements statements();

    /**
     * Returns the downloads of card transactions of a merchant.
     *
     * @return the merchant downloads
     */
    Merchant merchant();

    /**
     * Returns the calls that move the bookmark of the last downloaded movement.
     *
     * @return the bookmark calls
     */
    Bookmark bookmark();

    /**
     * Returns the submission of payment orders.
     *
     * @return the payment calls
     */
    Payments payments();

    /**
     * Starts building a client for the account the token belongs to.
     *
     * @param token the API token created in Fio internet banking
     * @return a builder with the production defaults
     */
    static Builder builder(FioToken token) {
        return new Builder(token);
    }

    /**
     * Downloads of account movements.
     */
    interface Transactions {

        /**
         * Chooses the movements dated within a period, both ends included. Nothing changes on the bank's side.
         *
         * @param from the first day of the period, in the years 1 to 9999
         * @param to the last day of the period, not before {@code from}
         * @return the chosen download
         * @throws IllegalArgumentException when the period ends before it starts or a day is outside those years
         */
        TransactionRequest between(LocalDate from, LocalDate to);

        /**
         * Chooses the movements after the bookmark of the last download. When the answer holds movements, the bank
         * moves the bookmark to the newest of them, so a download that is lost before it is stored cannot be
         * repeated without {@link Bookmark moving the bookmark back}. A failure whose
         * {@link FioException#mayHaveTakenEffect()} is {@code true} is such a loss: the bank may have moved the
         * bookmark, so move it back to the last stored movement before downloading again.
         *
         * @return the chosen download
         */
        TransactionRequest sinceBookmark();
    }

    /**
     * A chosen download of account movements, fetched typed or downloaded raw.
     */
    interface TransactionRequest {

        /**
         * Downloads the movements as typed records (the JSON format).
         *
         * @return the statement
         * @throws FioHistoryLockedException when the period starts more than 90 days back and the account holder
         *         has not unlocked the full history for the token
         * @throws FioTooManyTransactionsException when the answer would hold more than 50 000 movements
         * @throws FioMalformedResponseException when the answer is not the JSON this client reads; after
         *         {@link Transactions#sinceBookmark()} the bank may have moved the bookmark, as described there
         * @throws FioException when the bank refuses or does not answer the request
         */
        AccountStatement fetch();

        /**
         * Downloads the movements in a raw format, as the bank sent them.
         *
         * @param format the format of the answer
         * @return the answer
         * @throws FioHistoryLockedException when the period starts more than 90 days back and the account holder
         *         has not unlocked the full history for the token
         * @throws FioTooManyTransactionsException when the answer would hold more than 50 000 movements
         * @throws FioException when the bank refuses or does not answer the request
         */
        Download download(TransactionFormat format);
    }

    /**
     * Downloads of the official account statements, which Fio numbers within each year.
     */
    interface Statements {

        /**
         * Chooses the most recently created official statement. Its number is asked from the bank by the first
         * of {@link StatementRequest#statementId()}, {@link StatementRequest#fetch()} and
         * {@link StatementRequest#download(StatementFormat)} called on the returned request and kept there, so a
         * fetch or download costs two requests, 30 seconds apart, and keeping the request saves asking again.
         * With {@link RateLimit#failFast()}, or a {@link RateLimit#waitUpTo(Duration) wait} under 30
         * seconds, the first fetch or download on a fresh request therefore fails with
         * {@link FioRateLimitException} once the number is known; call it again on the same request after
         * {@link FioRateLimitException#retryAfter()}.
         *
         * @return the chosen download
         */
        StatementRequest latest();

        /**
         * Chooses an official statement by its number.
         *
         * @param id the year and number of the statement
         * @return the chosen download
         */
        StatementRequest number(StatementId id);

        /**
         * Chooses an official statement by its number.
         *
         * @param year the year of the statement, four digits
         * @param number the number of the statement within the year, from 1
         * @return the chosen download
         * @throws IllegalArgumentException when the year is not four digits or the number is below 1, as
         *         {@link StatementId} describes
         */
        default StatementRequest number(int year, int number) {
            return number(new StatementId(year, number));
        }
    }

    /**
     * A chosen official statement, fetched typed or downloaded raw.
     */
    interface StatementRequest {

        /**
         * Returns the year and number of the statement; for {@link Statements#latest()} this asks the bank once
         * and keeps the answer.
         *
         * @return the statement number
         * @throws FioException when the bank refuses or does not answer the request
         */
        StatementId statementId();

        /**
         * Downloads the statement as typed records (the JSON format).
         *
         * @return the statement
         * @throws FioHistoryLockedException when the statement covers days more than 90 days back and the account
         *         holder has not unlocked the full history for the token, as Fio documents for every download
         * @throws FioException when the bank refuses or does not answer the request
         */
        AccountStatement fetch();

        /**
         * Downloads the statement in a raw format, for example as PDF.
         *
         * @param format the format of the answer
         * @return the answer
         * @throws FioHistoryLockedException when the statement covers days more than 90 days back and the account
         *         holder has not unlocked the full history for the token, as Fio documents for every download
         * @throws FioException when the bank refuses or does not answer the request
         */
        Download download(StatementFormat format);
    }

    /**
     * Downloads of card transactions of a merchant (payment terminals and the payment gateway).
     */
    interface Merchant {

        /**
         * Chooses the card transactions dated within a period, both ends included.
         *
         * @param from the first day of the period, in the years 1 to 9999
         * @param to the last day of the period, not before {@code from}
         * @return the chosen download
         * @throws IllegalArgumentException when the period ends before it starts or a day is outside those years
         */
        MerchantRequest between(LocalDate from, LocalDate to);
    }

    /**
     * A chosen download of card transactions, fetched typed or downloaded raw.
     */
    interface MerchantRequest {

        /**
         * Downloads the card transactions as typed records (the XML format).
         *
         * @return the merchant statement
         * @throws FioHistoryLockedException when the period starts more than 90 days back and the account holder
         *         has not unlocked the full history for the token, as Fio documents for every download
         * @throws FioException when the bank refuses or does not answer the request
         */
        MerchantStatement fetch();

        /**
         * Downloads the card transactions in a raw format, as the bank sent them.
         *
         * @param format the format of the answer
         * @return the answer
         * @throws FioHistoryLockedException when the period starts more than 90 days back and the account holder
         *         has not unlocked the full history for the token, as Fio documents for every download
         * @throws FioException when the bank refuses or does not answer the request
         */
        Download download(MerchantFormat format);
    }

    /**
     * The server-side bookmark that {@link Transactions#sinceBookmark()} continues from.
     */
    interface Bookmark {

        /**
         * Moves the bookmark behind a movement, so the next {@link Transactions#sinceBookmark()} starts with the
         * movement after it.
         *
         * @param transactionId the id of the last movement that was downloaded successfully, not negative
         * @throws FioException when the bank refuses or does not answer the request
         * @throws IllegalArgumentException when the id is negative
         */
        void moveAfterTransaction(long transactionId);

        /**
         * Moves the bookmark to a day, so the next {@link Transactions#sinceBookmark()} starts with that day's
         * movements.
         *
         * @param date the first day that was not downloaded successfully, in the years 1 to 9999
         * @throws FioException when the bank refuses or does not answer the request
         * @throws IllegalArgumentException when the day is outside those years
         */
        void moveToDate(LocalDate date);
    }

    /**
     * Submission of payment orders. A submitted batch is only accepted; an authorised person has to confirm it in
     * Fio internet banking before the bank processes it.
     */
    interface Payments {

        /**
         * Starts a batch of payment orders to submit.
         *
         * @return an empty batch
         */
        BatchRequest batch();

        /**
         * Submits a batch of payment orders built with this library.
         *
         * @param batch the orders
         * @return the bank's answer to the batch; a batch the bank checked and rejected is an answer whose
         *         {@link ImportResult#isAccepted()} is {@code false}, not an exception
         * @throws FioException when the bank does not answer, refuses the request before checking the orders, or
         *         answers in a way this client cannot read; when its {@link FioException#mayHaveTakenEffect()} is
         *         {@code true}, check the pending batches in Fio internet banking before submitting again
         * @throws IllegalArgumentException when the batch written as Fio XML is larger than
         *         {@link PaymentFile#MAXIMUM_SIZE}, the most the bank imports at once
         */
        ImportResult submit(PaymentBatch batch);

        /**
         * Submits a file of payment orders in one of the formats Fio imports.
         *
         * @param file the file and its format
         * @return the bank's answer to the batch; a batch the bank checked and rejected is an answer whose
         *         {@link ImportResult#isAccepted()} is {@code false}, not an exception
         * @throws FioException when the bank does not answer, refuses the request before checking the orders, or
         *         answers in a way this client cannot read; when its {@link FioException#mayHaveTakenEffect()} is
         *         {@code true}, check the pending batches in Fio internet banking before submitting again
         */
        ImportResult upload(PaymentFile file);
    }

    /**
     * A batch of payment orders being put together for submission. It is meant for one thread and one submission:
     * after {@link #submit()} it cannot be submitted again.
     */
    interface BatchRequest {

        /**
         * Adds a payment within the Czech Republic.
         *
         * @param order fills in the payment
         * @return this batch
         * @throws IllegalStateException when the filled-in payment misses a required part, or the batch was
         *         already submitted
         * @throws IllegalArgumentException when a part of the filled-in payment is invalid, as
         *         {@link DomesticPayment.Builder#build()} describes
         */
        BatchRequest domestic(Consumer<DomesticPayment.Builder> order);

        /**
         * Adds a SEPA payment in euro.
         *
         * @param order fills in the payment
         * @return this batch
         * @throws IllegalStateException when the filled-in payment misses a required part, or the batch was
         *         already submitted
         * @throws IllegalArgumentException when a part of the filled-in payment is invalid, as
         *         {@link EuroPayment.Builder#build()} describes
         */
        BatchRequest euro(Consumer<EuroPayment.Builder> order);

        /**
         * Adds a payment abroad outside SEPA.
         *
         * @param order fills in the payment
         * @return this batch
         * @throws IllegalStateException when the filled-in payment misses a required part, or the batch was
         *         already submitted
         * @throws IllegalArgumentException when a part of the filled-in payment is invalid, as
         *         {@link ForeignPayment.Builder#build()} describes
         */
        BatchRequest foreign(Consumer<ForeignPayment.Builder> order);

        /**
         * Adds a ready payment order.
         *
         * @param order the order
         * @return this batch
         * @throws IllegalStateException when the batch was already submitted
         */
        BatchRequest add(PaymentOrder order);

        /**
         * Submits the batch.
         *
         * @return the bank's answer to the batch; a batch the bank checked and rejected is an answer whose
         *         {@link ImportResult#isAccepted()} is {@code false}, not an exception
         * @throws FioException when the bank does not answer, refuses the request before checking the orders, or
         *         answers in a way this client cannot read; when its {@link FioException#mayHaveTakenEffect()} is
         *         {@code true}, check the pending batches in Fio internet banking before submitting again
         * @throws IllegalStateException when the batch is empty, its orders are not all from the same account (the
         *         one the token belongs to) and in the same currency, the batch written as Fio XML is larger than
         *         {@link PaymentFile#MAXIMUM_SIZE}, or it was already submitted; the account, currency and size
         *         checks are an {@link IllegalArgumentException} from {@link PaymentBatch} and
         *         {@link Payments#submit(PaymentBatch)}, where the batch is an argument
         */
        ImportResult submit();
    }

    /**
     * Builds a {@link FioClient}. Every setting has a production default.
     *
     * <p>The default HTTP client is created by {@link #build()} and lives with the {@link FioClient}; its threads
     * end when the client is no longer referenced. To close it earlier, or to share it, pass one through
     * {@link #httpClient(HttpClient)} and manage it yourself.
     */
    final class Builder {

        private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
        private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(2);
        private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "[::1]");
        private static final String HTTPS = "https";
        private static final String HTTP = "http";

        private final FioToken token;
        private URI baseUri = DEFAULT_BASE_URI;
        private boolean plainHttpAllowed;
        private @Nullable HttpClient httpClient;
        private Clock clock = Clock.systemUTC();
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private RateLimit rateLimit = RateLimit.defaults();
        private Sleeper sleeper = Sleeper.THREAD;
        private Locale messageLocale = Locale.ENGLISH;
        private int responseSizeLimit = FioHttp.RESPONSE_SIZE_LIMIT;

        private Builder(FioToken token) {
            this.token = requireNonNull(token, "token");
        }

        /**
         * Points the client at another address, for example a stub server in tests. The token travels in the
         * request path, so the address has to be {@code https}; plain {@code http} is accepted only for the
         * loopback addresses {@code 127.0.0.1} and {@code [::1]}, or with {@link #allowPlainHttp()}, which
         * {@link #build()} checks, so the two can be called in either order.
         *
         * @param baseUri an absolute {@code https} or {@code http} URI with a host and without user info, query
         *        or fragment, {@link #DEFAULT_BASE_URI} by default
         * @return this builder
         * @throws IllegalArgumentException when the URI is relative, has no host the JDK can send a request to,
         *         carries user info, a query or a fragment, or its scheme is neither {@code https} nor
         *         {@code http}
         */
        public Builder baseUri(URI baseUri) {
            requireNonNull(baseUri, "baseUri");
            if (!baseUri.isAbsolute()) {
                throw new IllegalArgumentException("The Fio API base URI must be absolute: " + baseUri);
            }
            if (baseUri.getHost() == null) {
                throw new IllegalArgumentException("The Fio API base URI must name a host: " + baseUri);
            }
            if (baseUri.getRawUserInfo() != null || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "The Fio API base URI must not carry user info, a query or a fragment, the request path is appended to it: " + baseUri);
            }
            if (!HTTPS.equalsIgnoreCase(baseUri.getScheme()) && !HTTP.equalsIgnoreCase(baseUri.getScheme())) {
                throw new IllegalArgumentException(httpsRequired(baseUri));
            }
            this.baseUri = baseUri;
            return this;
        }

        /**
         * Lets the {@link #baseUri(URI) base URI} be plain {@code http} to any host, for a stub on another machine
         * or in another container, whether it is set before or after this call. Every request then carries the
         * token unencrypted to that host, so this is for tests only; the production address needs nothing of the
         * kind.
         *
         * @return this builder
         */
        public Builder allowPlainHttp() {
            this.plainHttpAllowed = true;
            return this;
        }

        /**
         * Sends the requests through a given HTTP client, for example one with a proxy or a custom SSL context.
         * The client must not follow redirects, since a redirect could carry the token to another host; the
         * JDK's default, {@link HttpClient.Redirect#NEVER}, is right.
         *
         * @param httpClient the client; by default one with a 10-second connect timeout
         * @return this builder
         * @throws IllegalArgumentException when the client follows redirects
         */
        public Builder httpClient(HttpClient httpClient) {
            requireNonNull(httpClient, "httpClient");
            if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
                throw new IllegalArgumentException(
                        "The HTTP client must not follow redirects (Redirect.NEVER), a redirect could carry the token elsewhere: "
                                + httpClient.followRedirects());
            }
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Reads the time from a given clock, which drives the rate limit and
         * {@link FioRateLimitException#retryAfter()}.
         *
         * @param clock the clock, {@link Clock#systemUTC()} by default
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = requireNonNull(clock, "clock");
            return this;
        }

        /**
         * Gives up on a request that is not answered in time.
         *
         * @param requestTimeout a positive duration, 2 minutes by default
         * @return this builder
         * @throws IllegalArgumentException when the duration is zero or negative
         */
        public Builder requestTimeout(Duration requestTimeout) {
            requireNonNull(requestTimeout, "requestTimeout");
            if (requestTimeout.isNegative() || requestTimeout.isZero()) {
                throw new IllegalArgumentException("The request timeout must be positive: " + requestTimeout);
            }
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Chooses how the client keeps to the limit of one request per 30 seconds.
         *
         * @param rateLimit the policy, {@link RateLimit#defaults()} by default
         * @return this builder
         */
        public Builder rateLimit(RateLimit rateLimit) {
            this.rateLimit = requireNonNull(rateLimit, "rateLimit");
            return this;
        }

        /**
         * Chooses the language of the messages in the bank's answers to submitted payments.
         *
         * @param locale Czech, Slovak or English; English by default
         * @return this builder
         * @throws IllegalArgumentException when the locale's language is not Czech, Slovak or English
         */
        public Builder messageLocale(Locale locale) {
            requireNonNull(locale, "locale");
            if (!FioHttp.MESSAGE_LANGUAGES.contains(locale.getLanguage())) {
                throw new IllegalArgumentException("Fio answers in Czech, Slovak or English, not in '" + locale + "'");
            }
            this.messageLocale = locale;
            return this;
        }

        Builder sleeper(Sleeper sleeper) {
            this.sleeper = requireNonNull(sleeper, "sleeper");
            return this;
        }

        private static String httpsRequired(URI uri) {
            return "The Fio API base URI must use https, the token travels in it; plain http is accepted only for "
                    + "127.0.0.1 and [::1], or after allowPlainHttp(): " + uri;
        }

        Sleeper sleeper() {
            return sleeper;
        }

        /**
         * Stops reading an answer that grows beyond a size, so that a misbehaving host cannot exhaust the heap.
         * The default, 256 MiB, holds the largest answer the bank documents, 50 000 movements with every field
         * at its longest; a client that only ever downloads short periods can go lower.
         *
         * @param bytes the most bytes of one answer the client reads, positive; an answer longer than that fails
         *        with a {@link FioResponseTooLargeException}
         * @return this builder
         * @throws IllegalArgumentException when the size is not positive
         */
        public Builder maximumResponseSize(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("The maximum response size must be positive: " + bytes);
            }
            this.responseSizeLimit = bytes;
            return this;
        }

        /**
         * Builds the client. Each call builds an independent client with its own rate limiting and, unless one
         * was {@link #httpClient(HttpClient) given}, its own HTTP client; two clients built for the same token
         * cannot keep the 30 seconds between each other's requests, so build one and share it.
         *
         * @return the client
         * @throws IllegalStateException when the base URI is plain {@code http} to a host other than
         *         {@code 127.0.0.1} and {@code [::1]} and {@link #allowPlainHttp()} was not called
         */
        public FioClient build() {
            if (HTTP.equalsIgnoreCase(baseUri.getScheme()) && !plainHttpAllowed && !LOOPBACK_HOSTS.contains(baseUri.getHost())) {
                throw new IllegalStateException(httpsRequired(baseUri));
            }
            RequestPacer pacer = new RequestPacer(rateLimit, clock, sleeper);
            FioHttp http = new FioHttp(
                    token, baseUri, httpClientOrDefault(), clock, requestTimeout, pacer, messageLocale, responseSizeLimit);
            return new HttpFioClient(http);
        }

        HttpClient httpClientOrDefault() {
            return httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(DEFAULT_CONNECT_TIMEOUT).build();
        }
    }
}
