package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.StubBank.SECRET;
import static io.github.bzumik1.fio.StubBank.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

class ExceptionSerializationTest {

    @Test
    void should_keep_message_masked_cause_and_reach_flag_when_serialized_given_transport_failure() throws Exception {
        Throwable cause = TokenMaskedFailure.withoutToken(new IOException("Cannot reach /last/" + SECRET + "/transactions.json"), TOKEN);
        FioTransportException failure = new FioTransportException("Fio API request GET /last/***/transactions.json failed", cause, true);

        FioTransportException copy = roundTrip(failure);

        assertThat(copy).hasMessage("Fio API request GET /last/***/transactions.json failed");
        assertThat(copy.mayHaveReachedTheBank()).isTrue();
        assertThat(copy.getCause()).isExactlyInstanceOf(TokenMaskedFailure.class)
                .hasMessage("java.io.IOException: Cannot reach /last/***/transactions.json");
        assertThat(Throwables.printed(copy)).doesNotContain(SECRET);
    }

    @Test
    void should_keep_that_the_request_never_left_when_serialized_given_transport_failure_before_sending() throws Exception {
        FioTransportException copy = roundTrip(new FioTransportException("Connection refused", null, false));

        assertThat(copy.mayHaveReachedTheBank()).isFalse();
        assertThat(copy).hasMessage("Connection refused").hasNoCause();
    }

    @ParameterizedTest
    @MethodSource("everyFailureType")
    void should_keep_message_status_body_and_own_details_when_serialized_given_each_failure_type(
            FioException failure, Function<FioException, Object> detail, Object expectedDetail) throws Exception {
        FioException copy = roundTrip(failure);

        assertThat(copy).isExactlyInstanceOf(failure.getClass()).hasMessage(failure.getMessage());
        assertThat(copy.statusCode()).isEqualTo(failure.statusCode());
        assertThat(copy.responseBody()).isEqualTo(failure.responseBody());
        assertThat(copy.mayHaveTakenEffect()).isEqualTo(failure.mayHaveTakenEffect());
        assertThat(detail.apply(copy)).isEqualTo(expectedDetail);
    }

    private static Stream<Arguments> everyFailureType() {
        Instant retryAfter = Instant.parse("2026-10-17T08:00:30Z");
        return Stream.of(
                Arguments.of(new FioException("refused", 404, "Chyba"), detail(FioException::statusCode), OptionalInt.of(404)),
                Arguments.of(new FioRateLimitException("refused", "wait", retryAfter),
                        detail(failure -> ((FioRateLimitException) failure).retryAfter()), retryAfter),
                Arguments.of(new FioAuthorizationException("refused", 401, "bad token"),
                        detail(FioException::responseBody), Optional.of("bad token")),
                Arguments.of(new FioHistoryLockedException("refused", StubFioServer.historyLockedBody(LocalDate.of(2026, 7, 19))),
                        detail(failure -> ((FioHistoryLockedException) failure).earliestAvailableDay()),
                        Optional.of(LocalDate.of(2026, 7, 19))),
                Arguments.of(new FioTooManyTransactionsException("refused", "too many"), detail(FioException::statusCode), OptionalInt.of(413)),
                Arguments.of(new FioMalformedResponseException("not understood", 200, "<html>", new IllegalStateException("bad")),
                        detail(failure -> failure.getCause().getMessage()), "bad"),
                Arguments.of(new FioTransportException("failed", null, true),
                        detail(failure -> ((FioTransportException) failure).mayHaveReachedTheBank()), true),
                Arguments.of(new FioResponseTooLargeException("too long", 1024, new IllegalStateException("cut")),
                        detail(failure -> ((FioResponseTooLargeException) failure).limit()), 1024));
    }

    private static Function<FioException, Object> detail(Function<FioException, Object> detail) {
        return detail;
    }

    private static <T extends FioException> T roundTrip(T failure) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(failure);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            T copy = (T) input.readObject();
            return copy;
        }
    }
}
