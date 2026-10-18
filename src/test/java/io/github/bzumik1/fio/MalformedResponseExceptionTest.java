package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MalformedResponseExceptionTest {

    @Test
    void should_keep_the_value_whole_when_excerpted_given_value_of_200_characters() {
        String value = "v".repeat(200);

        assertThat(MalformedResponseException.excerpt(value)).isEqualTo(value);
    }

    @Test
    void should_keep_the_first_200_characters_and_mark_the_cut_when_excerpted_given_value_of_201_characters() {
        assertThat(MalformedResponseException.excerpt("v".repeat(200) + "w")).isEqualTo("v".repeat(200) + "…");
    }
}
