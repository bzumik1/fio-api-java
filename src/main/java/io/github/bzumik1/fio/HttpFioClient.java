package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class HttpFioClient implements FioClient {

    private static final String JSON = "application/json";
    private static final String TRANSACTIONS = "transactions.";
    private static final int FIRST_YEAR = 1;
    private static final int LAST_YEAR = 9999;

    private final FioHttp http;
    private final AccountStatementParser statementParser = new AccountStatementParser();
    private final Transactions transactions = new HttpTransactions();
    private final Statements statements = new HttpStatements();
    private final Bookmark bookmark = new HttpBookmark();
    private final Merchant merchant;
    private final Payments payments;

    HttpFioClient(FioHttp http) {
        this.http = http;
        this.merchant = new HttpMerchant(http);
        this.payments = new HttpPayments(http);
    }

    @Override
    public Transactions transactions() {
        return transactions;
    }

    @Override
    public Statements statements() {
        return statements;
    }

    @Override
    public Merchant merchant() {
        return merchant;
    }

    @Override
    public Bookmark bookmark() {
        return bookmark;
    }

    @Override
    public Payments payments() {
        return payments;
    }

    @Override
    public String toString() {
        return "FioClient[" + http.token() + "]";
    }

    static void requirePeriod(LocalDate from, LocalDate to) {
        requireDay(from, "from");
        requireDay(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The period ends on " + to + ", before it starts on " + from);
        }
    }

    static LocalDate requireDay(LocalDate date, String name) {
        requireNonNull(date, name);
        if (date.getYear() < FIRST_YEAR || date.getYear() > LAST_YEAR) {
            throw new IllegalArgumentException("A day in the years " + FIRST_YEAR + " to " + LAST_YEAR + " is required, not " + date);
        }
        return date;
    }

    static String iso(LocalDate date) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
    }

    private final class HttpTransactions implements Transactions {

        @Override
        public TransactionRequest between(LocalDate from, LocalDate to) {
            requirePeriod(from, to);
            return new HttpTransactionRequest("periods", http.token().value(), iso(from), iso(to));
        }

        @Override
        public TransactionRequest sinceBookmark() {
            return new HttpTransactionRequest("last", http.token().value());
        }
    }

    private final class HttpTransactionRequest implements TransactionRequest {

        private final String[] path;

        private HttpTransactionRequest(String... path) {
            this.path = path;
        }

        @Override
        public AccountStatement fetch() {
            return http.parsed(http.get(JSON, path(TRANSACTIONS + "json")), statementParser::parse);
        }

        @Override
        public Download download(TransactionFormat format) {
            requireNonNull(format, "format");
            return http.get(format.mediaType(), path(TRANSACTIONS + format.extension())).document(format.charset());
        }

        private String[] path(String last) {
            String[] segments = new String[path.length + 1];
            System.arraycopy(path, 0, segments, 0, path.length);
            segments[path.length] = last;
            return segments;
        }
    }

    private final class HttpStatements implements Statements {

        @Override
        public StatementRequest latest() {
            return new HttpStatementRequest(() -> http.parsed(
                    http.get("text/plain", "lastStatement", http.token().value(), "statement"),
                    body -> StatementId.parse(new String(body, StandardCharsets.UTF_8))));
        }

        @Override
        public StatementRequest number(StatementId id) {
            requireNonNull(id, "id");
            return new HttpStatementRequest(() -> id);
        }
    }

    private final class HttpStatementRequest implements StatementRequest {

        private final Supplier<StatementId> lookup;
        private final ReentrantLock resolving = new ReentrantLock();
        private volatile @Nullable StatementId resolved;

        private HttpStatementRequest(Supplier<StatementId> lookup) {
            this.lookup = lookup;
        }

        @Override
        public StatementId statementId() {
            StatementId known = resolved;
            if (known != null) {
                return known;
            }
            resolving.lock();
            try {
                known = resolved;
                if (known == null) {
                    known = lookup.get();
                    resolved = known;
                }
                return known;
            } finally {
                resolving.unlock();
            }
        }

        @Override
        public AccountStatement fetch() {
            return http.parsed(http.get(JSON, path(statementId(), TRANSACTIONS + "json")), statementParser::parse);
        }

        @Override
        public Download download(StatementFormat format) {
            requireNonNull(format, "format");
            return http.get(format.mediaType(), path(statementId(), TRANSACTIONS + format.extension())).document(format.charset());
        }

        private String[] path(StatementId statement, String last) {
            return new String[] {
                    "by-id", http.token().value(), Integer.toString(statement.year()), Integer.toString(statement.number()), last
            };
        }
    }

    private final class HttpBookmark implements Bookmark {

        @Override
        public void moveAfterTransaction(long transactionId) {
            if (transactionId < 0) {
                throw new IllegalArgumentException("A transaction id is never negative: " + transactionId);
            }
            http.get("*/*", "set-last-id", http.token().value(), Long.toString(transactionId), "");
        }

        @Override
        public void moveToDate(LocalDate date) {
            http.get("*/*", "set-last-date", http.token().value(), iso(requireDay(date, "date")), "");
        }
    }
}
