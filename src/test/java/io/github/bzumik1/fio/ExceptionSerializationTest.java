package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

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

    private static FioTransportException roundTrip(FioTransportException failure) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(failure);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (FioTransportException) input.readObject();
        }
    }
}
