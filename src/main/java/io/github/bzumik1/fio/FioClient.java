package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
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
         * @param from the first day of the period
         * @param to the last day of the period, not before {@code from}
         * @return the chosen download
         */
        TransactionRequest between(LocalDate from, LocalDate to);

        /**
         * Chooses the movements after the bookmark of the last download. When the answer holds movements, the bank
         * moves the bookmark to the newest of them, so a download that is lost before it is stored cannot be
         * repeated without {@link Bookmark moving the bookmark back}. A {@link FioTransportException} whose
         * {@link FioTransportException#mayHaveReachedTheBank()} is {@code true} is such a loss: the bank may have
         * answered and moved the bookmark, so move it back to the last stored movement before downloading again.
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
         * Chooses the most recently created official statement. Its number is asked from the bank when the
         * download is fetched, so a fetch or download costs two requests, 30 seconds apart.
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
         * @param year the year of the statement
         * @param number the number of the statement within the year, from 1
         * @return the chosen download
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
         * Returns the year and number of the statement; for {@link Statements#latest()} this asks the bank.
         *
         * @return the statement number
         * @throws FioException when the bank refuses or does not answer the request
         */
        StatementId id();

        /**
         * Downloads the statement as typed records (the JSON format).
         *
         * @return the statement
         * @throws FioException when the bank refuses or does not answer the request
         */
        AccountStatement fetch();

        /**
         * Downloads the statement in a raw format, for example as PDF.
         *
         * @param format the format of the answer
         * @return the answer
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
         * @param from the first day of the period
         * @param to the last day of the period, not before {@code from}
         * @return the chosen download
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
         * @throws FioException when the bank refuses or does not answer the request
         */
        MerchantStatement fetch();

        /**
         * Downloads the card transactions in a raw format, as the bank sent them.
         *
         * @param format the format of the answer
         * @return the answer
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
         * @param transactionId the id of the last movement that was downloaded successfully
         * @throws FioException when the bank refuses or does not answer the request
         */
        void moveAfterTransaction(long transactionId);

        /**
         * Moves the bookmark to a day, so the next {@link Transactions#sinceBookmark()} starts with that day's
         * movements.
         *
         * @param date the first day that was not downloaded successfully
         * @throws FioException when the bank refuses or does not answer the request
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
         * @throws FioException when the bank does not answer, or refuses the request before checking the orders; a
         *         {@link FioTransportException} whose {@link FioTransportException#mayHaveReachedTheBank()} is
         *         {@code true} leaves the outcome unknown, so check the pending batches in Fio internet banking
         *         before submitting again
         */
        ImportResult submit(PaymentBatch batch);

        /**
         * Submits a file of payment orders in one of the formats Fio imports.
         *
         * @param file the file and its format
         * @return the bank's answer to the batch; a batch the bank checked and rejected is an answer whose
         *         {@link ImportResult#isAccepted()} is {@code false}, not an exception
         * @throws FioException when the bank does not answer, or refuses the request before checking the orders; a
         *         {@link FioTransportException} whose {@link FioTransportException#mayHaveReachedTheBank()} is
         *         {@code true} leaves the outcome unknown, so check the pending batches in Fio internet banking
         *         before submitting again
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
         */
        BatchRequest domestic(Consumer<DomesticPayment.Builder> order);

        /**
         * Adds a SEPA payment in euro.
         *
         * @param order fills in the payment
         * @return this batch
         */
        BatchRequest euro(Consumer<EuroPayment.Builder> order);

        /**
         * Adds a payment abroad outside SEPA.
         *
         * @param order fills in the payment
         * @return this batch
         */
        BatchRequest foreign(Consumer<ForeignPayment.Builder> order);

        /**
         * Adds a ready payment order.
         *
         * @param order the order
         * @return this batch
         */
        BatchRequest add(PaymentOrder order);

        /**
         * Submits the batch.
         *
         * @return the bank's answer to the batch; a batch the bank checked and rejected is an answer whose
         *         {@link ImportResult#isAccepted()} is {@code false}, not an exception
         * @throws FioException when the bank does not answer, or refuses the request before checking the orders; a
         *         {@link FioTransportException} whose {@link FioTransportException#mayHaveReachedTheBank()} is
         *         {@code true} leaves the outcome unknown, so check the pending batches in Fio internet banking
         *         before submitting again
         * @throws IllegalStateException when the batch is empty or was already submitted
         */
        ImportResult submit();
    }

    /**
     * Builds a {@link FioClient}. Every setting has a production default.
     */
    final class Builder {

        private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
        private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(2);

        private final FioToken token;
        private URI baseUri = DEFAULT_BASE_URI;
        private @Nullable HttpClient httpClient;
        private Clock clock = Clock.systemUTC();
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private RateLimit rateLimit = RateLimit.defaults();
        private Sleeper sleeper = Sleeper.THREAD;
        private Locale messageLocale = Locale.ENGLISH;

        private Builder(FioToken token) {
            this.token = requireNonNull(token, "token");
        }

        /**
         * Points the client at another address, for example a stub server in tests.
         *
         * @param baseUri an absolute URI, {@link #DEFAULT_BASE_URI} by default
         * @return this builder
         */
        public Builder baseUri(URI baseUri) {
            requireNonNull(baseUri, "baseUri");
            if (!baseUri.isAbsolute()) {
                throw new IllegalArgumentException("The Fio API base URI must be absolute: " + baseUri);
            }
            if (!"https".equals(baseUri.getScheme()) && !isLoopback(baseUri)) {
                throw new IllegalArgumentException("The Fio API base URI must use https, the token travels in it: " + baseUri);
            }
            this.baseUri = baseUri;
            return this;
        }

        /**
         * Sends the requests through a given HTTP client, for example one with a proxy or a custom SSL context.
         *
         * @param httpClient the client; by default one with a 10-second connect timeout
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = requireNonNull(httpClient, "httpClient");
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

        private static boolean isLoopback(URI uri) {
            String host = uri.getHost();
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        }

        /**
         * Builds the client.
         *
         * @return the client
         */
        public FioClient build() {
            HttpClient client = httpClient != null
                    ? httpClient
                    : HttpClient.newBuilder().connectTimeout(DEFAULT_CONNECT_TIMEOUT).build();
            RequestPacer pacer = new RequestPacer(rateLimit, clock, sleeper);
            FioHttp http = new FioHttp(token, baseUri, client, clock, requestTimeout, pacer, messageLocale);
            return new HttpFioClient(http);
        }
    }
}
