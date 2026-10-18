package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import static io.github.bzumik1.fio.StubBank.SECRET;
import static io.github.bzumik1.fio.StubBank.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FioHttpTest {

    private static final String REASON_START = "Fio API request GET x was answered with HTTP 200 and a body this client does not understand: ";

    private final FioHttp http = new FioHttp(TOKEN, URI.create("https://fio.example/v1/rest"), new RecordingHttpClient(200, new byte[0]),
            Clock.systemUTC(), Duration.ofSeconds(1), new RequestPacer(RateLimit.failFast(), Clock.systemUTC(), Sleeper.THREAD),
            Locale.ENGLISH, FioHttp.RESPONSE_SIZE_LIMIT);

    private FioMalformedResponseException failureOf(String reason) {
        FioHttp.FioResponse response = new FioHttp.FioResponse("Fio API request GET x", 200, null, "body".getBytes(StandardCharsets.UTF_8));
        return catchThrowableOfType(FioMalformedResponseException.class, () -> http.parsed(response, body -> {
            throw new MalformedResponseException(reason);
        }));
    }

    private FioMalformedResponseException failureWithBody(String body) {
        FioHttp.FioResponse response = new FioHttp.FioResponse("Fio API request GET x", 200, null, body.getBytes(StandardCharsets.UTF_8));
        return catchThrowableOfType(FioMalformedResponseException.class, () -> http.parsed(response, ignored -> {
            throw new MalformedResponseException("not a statement");
        }));
    }

    @Test
    void should_keep_the_first_16_ki_characters_when_parsing_fails_given_body_of_one_mebibyte() {
        FioMalformedResponseException failure = failureWithBody("y".repeat(1024 * 1024));

        assertThat(failure.responseBody()).hasValue("y".repeat(FioException.RESPONSE_BODY_LIMIT) + "\u2026");
        assertThat(failure.getMessage()).isEqualTo(REASON_START + "not a statement");
    }

    @Test
    void should_mask_the_whole_token_when_parsing_fails_given_body_cut_inside_the_token() {
        FioMalformedResponseException failure = failureWithBody("\u20ac".repeat(FioException.RESPONSE_BODY_LIMIT - 5) + SECRET + "z".repeat(100));

        assertThat(failure.responseBody()).hasValue("\u20ac".repeat(FioException.RESPONSE_BODY_LIMIT - 5) + "***zz\u2026");
    }

    @Test
    void should_mask_the_token_cut_by_the_decoded_part_when_parsing_fails_given_body_of_tokens_beyond_it() {
        String body = "x".repeat(10) + SECRET.repeat(2000) + "tail";

        FioMalformedResponseException failure = failureWithBody(body);

        assertThat(failure.responseBody()).hasValue("x".repeat(10) + "***".repeat(2000) + "tail");
    }

    @Test
    void should_cut_the_reason_to_the_body_limit_when_parsing_fails_given_reason_longer_than_the_limit() {
        FioMalformedResponseException failure = failureOf("y".repeat(20_000));

        assertThat(failure.getMessage()).isEqualTo(REASON_START + "y".repeat(FioException.RESPONSE_BODY_LIMIT) + "…");
    }

    @Test
    void should_keep_the_whole_reason_when_parsing_fails_given_reason_at_the_body_limit() {
        FioMalformedResponseException failure = failureOf("y".repeat(FioException.RESPONSE_BODY_LIMIT));

        assertThat(failure.getMessage()).isEqualTo(REASON_START + "y".repeat(FioException.RESPONSE_BODY_LIMIT));
    }

    @Test
    void should_mask_the_cut_beginning_of_the_token_when_parsing_fails_given_reason_cut_inside_the_token() {
        FioMalformedResponseException failure = failureOf("y".repeat(FioException.RESPONSE_BODY_LIMIT - 12) + SECRET);

        assertThat(failure.getMessage()).endsWith("y***…").doesNotContain(SECRET.substring(0, 12));
    }
}
