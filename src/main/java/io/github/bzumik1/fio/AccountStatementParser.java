package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AccountStatementParser {

    private static final Pattern COLUMN_KEY = Pattern.compile("column(\\d{1,9})");
    private static final Pattern DOMESTIC_ACCOUNT = Pattern.compile("(?:\\d{1,6}-)?\\d{1,10}");

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    AccountStatement parse(String json) {
        return parse(json.getBytes(StandardCharsets.UTF_8));
    }

    AccountStatement parse(byte[] json) {
        JsonNode statement = readTree(json).path("accountStatement");
        if (!statement.isObject()) {
            throw new MalformedResponseException("The body has no accountStatement object");
        }
        return new AccountStatement(
                info(statement.path("info")),
                transactions(statement.path("transactionList").path("transaction")));
    }

    private JsonNode readTree(byte[] json) {
        try {
            return mapper.readTree(json);
        } catch (JacksonException e) {
            throw new MalformedResponseException("The body is not valid JSON", e);
        }
    }

    private AccountInfo info(JsonNode info) {
        if (!info.isObject()) {
            throw new MalformedResponseException("The statement has no info object");
        }
        Function<String, RawValue> field = name -> new RawValue("info." + name, textOf(info.get(name)));
        Currency currency = field.apply("currency").requiredCurrency();
        Integer year = field.apply("yearList").optionalInt();
        Integer number = field.apply("idList").optionalInt();
        return new AccountInfo(
                account(field.apply("accountId"), field.apply("bankId")),
                field.apply("iban").requiredText(),
                field.apply("bic").requiredText(),
                currency,
                new Money(field.apply("openingBalance").requiredDecimal(), currency),
                new Money(field.apply("closingBalance").requiredDecimal(), currency),
                field.apply("dateStart").optionalDate(),
                field.apply("dateEnd").optionalDate(),
                year == null || number == null ? null : statementId(year, number),
                field.apply("idFrom").optionalLong(),
                field.apply("idTo").optionalLong(),
                field.apply("idLastDownload").optionalLong());
    }

    private static DomesticAccount account(RawValue accountId, RawValue bankId) {
        String number = accountId.requiredText();
        String bankCode = bankId.optionalText();
        try {
            return DomesticAccount.parse(bankCode == null ? number : number + "/" + bankCode);
        } catch (IllegalArgumentException e) {
            throw new MalformedResponseException(accountId.label() + " is not an account number: '" + number + "'", e);
        }
    }

    private static StatementId statementId(int year, int number) {
        try {
            return new StatementId(year, number);
        } catch (IllegalArgumentException e) {
            throw new MalformedResponseException("info.yearList and info.idList are not a statement number", e);
        }
    }

    private List<Transaction> transactions(JsonNode transactions) {
        if (transactions.isMissingNode() || transactions.isNull()) {
            return List.of();
        }
        if (!transactions.isArray()) {
            throw new MalformedResponseException("transactionList.transaction is not an array");
        }
        List<Transaction> parsed = new ArrayList<>();
        for (JsonNode transaction : transactions) {
            parsed.add(transaction(transaction));
        }
        return parsed;
    }

    private Transaction transaction(JsonNode transaction) {
        if (!transaction.isObject()) {
            throw new MalformedResponseException("A transaction is not an object");
        }
        Map<Integer, TransactionColumn> columns = columns(transaction);
        Function<Column, RawValue> column = known -> known.valueIn(columns);
        String typeLabel = column.apply(Column.TYPE).requiredText();
        return new Transaction(
                column.apply(Column.MOVEMENT_ID).requiredLong(),
                column.apply(Column.DATE).requiredDate(),
                new Money(column.apply(Column.AMOUNT).requiredDecimal(), column.apply(Column.CURRENCY).requiredCurrency()),
                TransactionType.fromLabel(typeLabel),
                typeLabel,
                counterparty(column),
                symbols(column),
                column.apply(Column.USER_IDENTIFICATION).optionalText(),
                column.apply(Column.EXECUTED_BY).optionalText(),
                column.apply(Column.MESSAGE_FOR_RECIPIENT).optionalText(),
                column.apply(Column.INSTRUCTION_ID).optionalLong(),
                column.apply(Column.DETAIL).optionalText(),
                column.apply(Column.COMMENT).optionalText(),
                column.apply(Column.PAYER_REFERENCE).optionalText(),
                columns);
    }

    private static Counterparty counterparty(Function<Column, RawValue> column) {
        return new Counterparty(
                counterpartyAccount(
                        column.apply(Column.COUNTERPARTY_ACCOUNT).optionalText(),
                        column.apply(Column.COUNTERPARTY_BANK_CODE).optionalText()),
                column.apply(Column.COUNTERPARTY_NAME).optionalText(),
                column.apply(Column.COUNTERPARTY_BANK_NAME).optionalText(),
                column.apply(Column.BIC).optionalText());
    }

    private static @Nullable BankAccount counterpartyAccount(@Nullable String account, @Nullable String bankCode) {
        if (account == null) {
            return null;
        }
        if (DOMESTIC_ACCOUNT.matcher(account).matches() && (bankCode == null || bankCode.matches("\\d{4}"))) {
            return DomesticAccount.parse(bankCode == null ? account : account + "/" + bankCode);
        }
        return new ForeignAccount(account);
    }

    private static PaymentSymbols symbols(Function<Column, RawValue> column) {
        String variable = column.apply(Column.VARIABLE_SYMBOL).optionalText();
        String constant = column.apply(Column.CONSTANT_SYMBOL).optionalText();
        String specific = column.apply(Column.SPECIFIC_SYMBOL).optionalText();
        try {
            return new PaymentSymbols(variable, constant, specific);
        } catch (IllegalArgumentException e) {
            throw new MalformedResponseException("The payment symbols are not digits", e);
        }
    }

    private static Map<Integer, TransactionColumn> columns(JsonNode transaction) {
        Map<Integer, TransactionColumn> columns = new TreeMap<>();
        for (Map.Entry<String, JsonNode> property : transaction.properties()) {
            JsonNode column = property.getValue();
            Integer id = column.isObject() ? columnId(property.getKey(), column) : null;
            if (id != null) {
                columns.put(id, new TransactionColumn(id, interned(textOf(column.get("name"))), textOf(column.get("value"))));
            }
        }
        return columns;
    }

    private static @Nullable String interned(@Nullable String name) {
        return name == null ? null : name.intern();
    }

    private static @Nullable Integer columnId(String key, JsonNode column) {
        JsonNode id = column.get("id");
        if (id != null && id.isIntegralNumber() && id.canConvertToInt()) {
            return id.intValue();
        }
        Matcher matcher = COLUMN_KEY.matcher(key);
        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static @Nullable String textOf(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isString()) {
            return node.stringValue();
        }
        if (node.isNumber()) {
            return node.decimalValue().toPlainString();
        }
        return node.toString();
    }

    private enum Column {
        DATE(0, "Datum"),
        AMOUNT(1, "Objem"),
        COUNTERPARTY_ACCOUNT(2, "Protiúčet"),
        COUNTERPARTY_BANK_CODE(3, "Kód banky"),
        CONSTANT_SYMBOL(4, "KS"),
        VARIABLE_SYMBOL(5, "VS"),
        SPECIFIC_SYMBOL(6, "SS"),
        USER_IDENTIFICATION(7, "Uživatelská identifikace"),
        TYPE(8, "Typ"),
        EXECUTED_BY(9, "Provedl"),
        COUNTERPARTY_NAME(10, "Název protiúčtu"),
        COUNTERPARTY_BANK_NAME(12, "Název banky"),
        CURRENCY(14, "Měna"),
        MESSAGE_FOR_RECIPIENT(16, "Zpráva pro příjemce"),
        INSTRUCTION_ID(17, "ID pokynu"),
        DETAIL(18, "Upřesnění"),
        MOVEMENT_ID(22, "ID pohybu"),
        COMMENT(25, "Komentář"),
        BIC(26, "BIC"),
        PAYER_REFERENCE(27, "Reference plátce");

        private final int id;
        private final String documentedName;

        Column(int id, String documentedName) {
            this.id = id;
            this.documentedName = documentedName;
        }

        RawValue valueIn(Map<Integer, TransactionColumn> columns) {
            TransactionColumn column = columns.get(id);
            return new RawValue("column " + id + " (" + documentedName + ")", column == null ? null : column.value());
        }
    }
}
