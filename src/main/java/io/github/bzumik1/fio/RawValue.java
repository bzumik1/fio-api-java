package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Currency;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record RawValue(String label, @Nullable String text) {

    private static final ZoneId BANK_TIME_ZONE = ZoneId.of("Europe/Prague");
    private static final Pattern DATE_WITH_OPTIONAL_OFFSET = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern EPOCH_MILLISECONDS = Pattern.compile("-?\\d{1,18}");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");

    @Nullable String optionalText() {
        return text == null || text.isBlank() ? null : text;
    }

    String requiredText() {
        String value = optionalText();
        if (value == null) {
            throw new MalformedResponseException(label + " is missing");
        }
        return value;
    }

    BigDecimal requiredDecimal() {
        return converted(requiredText(), RawValue::toDecimal, "a decimal number");
    }

    @Nullable BigDecimal optionalDecimal() {
        String value = optionalText();
        return value == null ? null : converted(value, RawValue::toDecimal, "a decimal number");
    }

    long requiredLong() {
        return converted(requiredText(), Long::valueOf, "a whole number");
    }

    @Nullable Long optionalLong() {
        String value = optionalText();
        return value == null ? null : converted(value, Long::valueOf, "a whole number");
    }

    @Nullable Integer optionalInt() {
        String value = optionalText();
        return value == null ? null : converted(value, Integer::valueOf, "a whole number");
    }

    Currency requiredCurrency() {
        return converted(requiredText(), Currency::getInstance, "an ISO 4217 currency code");
    }

    LocalDate requiredDate() {
        return converted(requiredText(), RawValue::toDate, "a date");
    }

    @Nullable LocalDate optionalDate() {
        String value = optionalText();
        return value == null ? null : converted(value, RawValue::toDate, "a date");
    }

    @Nullable OffsetDateTime optionalDateTime() {
        String value = optionalText();
        return value == null ? null : converted(value, RawValue::toDateTime, "a date and time");
    }

    @Nullable Boolean optionalBoolean() {
        String value = optionalText();
        return value == null ? null : converted(value, RawValue::toBoolean, "true or false");
    }

    static BigDecimal toDecimal(String value) {
        if (!DECIMAL.matcher(value).matches()) {
            throw new NumberFormatException("Not a plain decimal number");
        }
        return new BigDecimal(value);
    }

    private static LocalDate toDate(String value) {
        Matcher date = DATE_WITH_OPTIONAL_OFFSET.matcher(value);
        if (date.matches()) {
            return LocalDate.parse(date.group(1));
        }
        if (EPOCH_MILLISECONDS.matcher(value).matches()) {
            return Instant.ofEpochMilli(Long.parseLong(value)).atZone(BANK_TIME_ZONE).toLocalDate();
        }
        throw new DateTimeException("Unknown date format");
    }

    private static OffsetDateTime toDateTime(String value) {
        TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(value, OffsetDateTime::from, LocalDateTime::from);
        if (parsed instanceof OffsetDateTime withOffset) {
            return withOffset;
        }
        return ((LocalDateTime) parsed).atZone(BANK_TIME_ZONE).toOffsetDateTime();
    }

    private static Boolean toBoolean(String value) {
        return switch (value) {
            case "true", "1" -> Boolean.TRUE;
            case "false", "0" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("Not a boolean");
        };
    }

    private <T> T converted(String value, Function<String, T> conversion, String expected) {
        try {
            return conversion.apply(value);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new MalformedResponseException(label + " is not " + expected + ": '" + value + "'", e);
        }
    }
}
