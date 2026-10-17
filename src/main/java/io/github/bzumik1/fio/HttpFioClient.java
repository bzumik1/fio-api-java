package io.github.bzumik1.fio;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class HttpFioClient implements FioClient {

    private static final String JSON = "application/json";
    private static final String TRANSACTIONS = "transactions.";

    private final FioHttp http;
    private final FioStatementParser statementParser = new FioStatementParser();
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
        requireNonNull(from, "from");
        requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The period ends on " + to + ", before it starts on " + from);
        }
    }

    static String iso(LocalDate date) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
    }

    private final class HttpTransactions implements Transactions {

        @Override
        public TransactionRequest between(LocalDate from, LocalDate to) {
            requirePeriod(from, to);
            return new Download("periods", http.token().value(), iso(from), iso(to));
        }

        @Override
        public TransactionRequest sinceBookmark() {
            return new Download("last", http.token().value());
        }
    }

    private final class Download implements TransactionRequest {

        private final String[] path;

        private Download(String... path) {
            this.path = path;
        }

        @Override
        public FioStatement fetch() {
            return http.parsed(http.get(JSON, path(TRANSACTIONS + "json")), statementParser::parse);
        }

        @Override
        public FioDocument download(TransactionFormat format) {
            requireNonNull(format, "format");
            return http.get(format.mediaType(), path(TRANSACTIONS + format.extension())).document();
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
            return new Statement(() -> http.parsed(
                    http.get("text/plain", "lastStatement", http.token().value(), "statement"),
                    StatementId::parse));
        }

        @Override
        public StatementRequest number(StatementId id) {
            requireNonNull(id, "id");
            return new Statement(() -> id);
        }
    }

    private final class Statement implements StatementRequest {

        private final Supplier<StatementId> id;

        private Statement(Supplier<StatementId> id) {
            this.id = id;
        }

        @Override
        public StatementId id() {
            return id.get();
        }

        @Override
        public FioStatement fetch() {
            return http.parsed(http.get(JSON, path(id.get(), TRANSACTIONS + "json")), statementParser::parse);
        }

        @Override
        public FioDocument download(StatementFormat format) {
            requireNonNull(format, "format");
            return http.get(format.mediaType(), path(id.get(), TRANSACTIONS + format.extension())).document();
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
            requireNonNull(date, "date");
            http.get("*/*", "set-last-date", http.token().value(), iso(date), "");
        }
    }
}
