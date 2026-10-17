package cz.novanadeje.fio;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TokenMaskedFailureTest {

    private static final String SECRET = "aGEMtmwcsWAjPzhg3bPH3j7Iu15g56d66AdEbfIjqIgMR9Idh6u3";
    private static final FioToken TOKEN = FioToken.of(SECRET);

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
}
