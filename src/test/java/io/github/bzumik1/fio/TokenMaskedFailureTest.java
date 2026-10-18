package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.github.bzumik1.fio.StubBank.SECRET;
import static io.github.bzumik1.fio.StubBank.TOKEN;
import static io.github.bzumik1.fio.StubBank.printed;
import static org.assertj.core.api.Assertions.assertThat;

class TokenMaskedFailureTest {

    @Test
    void should_mask_every_link_once_when_masking_given_causes_that_form_a_cycle() {
        IOException first = new IOException("first " + SECRET);
        IOException second = new IOException("second " + SECRET, first);
        first.initCause(second);

        Throwable masked = TokenMaskedFailure.withoutToken(first, TOKEN);

        assertThat(masked).hasMessage("java.io.IOException: first ***");
        assertThat(masked.getCause()).hasMessage("java.io.IOException: second ***");
        assertThat(masked.getCause().getCause()).isNull();
        assertThat(masked.getStackTrace()).isEqualTo(first.getStackTrace());
    }

    @Test
    void should_mask_suppressed_failure_when_masking_given_token_only_in_suppressed_exception() {
        IOException failure = new IOException("Connection reset");
        failure.addSuppressed(new IllegalStateException("closing " + SECRET));

        Throwable masked = TokenMaskedFailure.withoutToken(failure, TOKEN);

        assertThat(masked).hasMessage("java.io.IOException: Connection reset");
        assertThat(masked.getSuppressed()).singleElement()
                .satisfies(suppressed -> assertThat(suppressed).hasMessage("java.lang.IllegalStateException: closing ***"));
        assertThat(printed(masked)).doesNotContain(SECRET);
    }

    @Test
    void should_mask_suppressed_failure_of_cause_when_masking_given_token_only_deep_in_the_chain() {
        IOException cause = new IOException("cause");
        cause.addSuppressed(new IOException("plain"));
        cause.addSuppressed(new IOException("closing " + SECRET));
        IOException failure = new IOException("failure", cause);

        Throwable masked = TokenMaskedFailure.withoutToken(failure, TOKEN);

        assertThat(masked.getCause().getSuppressed()).extracting(Throwable::getMessage)
                .containsExactly("java.io.IOException: plain", "java.io.IOException: closing ***");
        assertThat(printed(masked)).doesNotContain(SECRET);
    }

    @Test
    void should_copy_each_failure_once_when_masking_given_cause_that_suppresses_its_own_parent() {
        IOException cause = new IOException("cause " + SECRET);
        IOException failure = new IOException("failure", cause);
        cause.addSuppressed(failure);

        Throwable masked = TokenMaskedFailure.withoutToken(failure, TOKEN);

        assertThat(masked.getCause()).hasMessage("java.io.IOException: cause ***");
        assertThat(masked.getCause().getSuppressed()).isEmpty();
    }

    @Test
    void should_return_same_failure_when_masking_given_no_token_in_failure_cause_or_suppressed() {
        IOException cause = new IOException("cause");
        IOException failure = new IOException("failure", cause);
        failure.addSuppressed(new IOException("suppressed"));
        cause.addSuppressed(failure);

        assertThat(TokenMaskedFailure.withoutToken(failure, TOKEN)).isSameAs(failure);
    }
}
