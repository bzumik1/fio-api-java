package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * How a {@link FioClient} keeps to Fio's limit of one request per token every
 * {@link FioClient#MINIMUM_REQUEST_INTERVAL 30 seconds}.
 *
 * <p>With {@link #waitUpTo(Duration)} the client spaces its own requests 30 seconds apart, and when the bank still
 * answers HTTP 409 (the token was used elsewhere) it waits for the next slot and tries again, until the total time
 * spent waiting on one call would exceed the limit; then it throws {@link FioRateLimitException}. With
 * {@link #failFast()} the client never waits: a request too soon after the previous one, or one the bank refuses,
 * throws {@link FioRateLimitException} at once, and {@link FioRateLimitException#retryAfter()} says when to try again.
 */
public final class RateLimit {

    private static final Duration DEFAULT_MAXIMUM_WAIT = Duration.ofMinutes(2);
    private static final Duration LONGEST_WAIT = Duration.ofDays(1);

    private final Duration maximumWait;

    private RateLimit(Duration maximumWait) {
        this.maximumWait = maximumWait;
    }

    /**
     * Waits for a free slot, at most two minutes per call.
     *
     * @return the default policy
     */
    public static RateLimit defaults() {
        return waitUpTo(DEFAULT_MAXIMUM_WAIT);
    }

    /**
     * Waits for a free slot, at most the given time per call.
     *
     * @param maximumWait the longest total wait one call may spend, positive and at most one day
     * @return the policy
     * @throws IllegalArgumentException when the duration is not positive or longer than one day
     */
    public static RateLimit waitUpTo(Duration maximumWait) {
        requireNonNull(maximumWait, "maximumWait");
        if (maximumWait.isNegative() || maximumWait.isZero()) {
            throw new IllegalArgumentException("The maximum wait must be positive: " + maximumWait);
        }
        if (maximumWait.compareTo(LONGEST_WAIT) > 0) {
            throw new IllegalArgumentException("The maximum wait is at most " + LONGEST_WAIT + ": " + maximumWait);
        }
        return new RateLimit(maximumWait);
    }

    /**
     * Never waits; the caller handles {@link FioRateLimitException} itself.
     *
     * @return the policy
     */
    public static RateLimit failFast() {
        return new RateLimit(Duration.ZERO);
    }

    /**
     * Returns the longest total wait one call may spend; zero for {@link #failFast()}.
     *
     * @return the maximum wait
     */
    public Duration maximumWait() {
        return maximumWait;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof RateLimit that && that.maximumWait.equals(maximumWait);
    }

    @Override
    public int hashCode() {
        return maximumWait.hashCode();
    }

    @Override
    public String toString() {
        return maximumWait.isZero() ? "RateLimit[fail fast]" : "RateLimit[wait up to " + maximumWait + "]";
    }
}
