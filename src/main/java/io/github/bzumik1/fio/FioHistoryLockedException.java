package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The request asked for movements older than 90 days, which the bank gives only for 10 minutes after the account
 * holder unlocks the full history for the token in Fio internet banking (Nastavení, API, the lock icon). The bank
 * answers HTTP 422. Fio's documentation says nothing about the body; the bank was seen to send a Czech sentence
 * that names the earliest day available without unlocking, which {@link #earliestAvailableDay()} reads when it
 * is there.
 */
public final class FioHistoryLockedException extends FioException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int UNPROCESSABLE_ENTITY = 422;
    private static final Pattern EARLIEST_DAY = Pattern.compile("\\(od (\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

    /**
     * Creates the exception.
     *
     * @param message what was refused, with the token masked
     * @param responseBody the body of the refusal, with the token masked
     */
    public FioHistoryLockedException(String message, @Nullable String responseBody) {
        super(message, UNPROCESSABLE_ENTITY, responseBody);
    }

    /**
     * Returns the earliest day the bank offers without unlocking, when its answer names one: a request from that
     * day on succeeds.
     *
     * @return the earliest available day, or empty when the answer does not name one
     */
    public Optional<LocalDate> earliestAvailableDay() {
        Matcher day = EARLIEST_DAY.matcher(responseBody().orElse(""));
        if (!day.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(day.group(3)), Integer.parseInt(day.group(2)), Integer.parseInt(day.group(1))));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
