package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RawValueTest {

    @Test
    void should_name_only_the_first_200_characters_when_converted_given_value_of_1000_characters() {
        String value = "1".repeat(200) + "2".repeat(800);

        MalformedResponseException failure = catchThrowableOfType(MalformedResponseException.class,
                () -> new RawValue("column 1 (Objem)", value).requiredDecimal());

        assertThat(failure).hasMessage("column 1 (Objem) is not a decimal number: '" + "1".repeat(200) + "…'");
        assertThat(failure.getMessage()).doesNotContain("2");
    }
}
