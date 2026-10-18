package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.bzumik1.fio.StubBank.SECRET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FioTokenTest {

    @Test
    void should_hide_value_when_printed_given_token() {
        assertThat(FioToken.of(SECRET).toString())
                .doesNotContain(SECRET)
                .isEqualTo("FioToken[***]");
    }

    @Test
    void should_strip_surrounding_whitespace_when_created_given_value_read_from_file() {
        assertThat(FioToken.of("  " + SECRET + "\n")).isEqualTo(FioToken.of(SECRET));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n"})
    void should_reject_when_created_given_no_value(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> FioToken.of(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc/def", "abc def", "abc?x=1", "abc%2F", "ab.cd"})
    void should_reject_without_echoing_value_when_created_given_characters_unsafe_in_url_path(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FioToken.of(value))
                .withMessageNotContaining(value);
    }

    @Test
    void should_replace_every_occurrence_when_masking_given_text_with_token() {
        FioToken token = FioToken.of(SECRET);

        assertThat(token.mask("GET /last/" + SECRET + "/transactions.json and " + SECRET))
                .isEqualTo("GET /last/***/transactions.json and ***");
    }

    @Test
    void should_find_value_when_asked_given_text_holding_token() {
        assertThat(FioToken.of(SECRET).occursIn("x" + SECRET + "y")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "nothing here")
    void should_not_find_value_when_asked_given_text_without_token(String text) {
        assertThat(FioToken.of(SECRET).occursIn(text)).isFalse();
    }

    @Test
    void should_leave_null_alone_when_masking_given_no_text() {
        assertThat(FioToken.of(SECRET).mask(null)).isNull();
    }

    @Test
    void should_equal_with_same_hash_code_when_compared_given_token_with_same_value() {
        assertThat(FioToken.of(SECRET)).isEqualTo(FioToken.of(SECRET)).hasSameHashCodeAs(FioToken.of(SECRET));
    }

    @Test
    void should_differ_when_compared_given_other_token_its_value_or_null() {
        assertThat(FioToken.of(SECRET)).isNotEqualTo(FioToken.of(SECRET + "x")).isNotEqualTo(SECRET).isNotEqualTo(null);
    }

    @Test
    void should_read_from_environment_when_asked_given_variable_with_token() {
        assertThat(FioToken.fromEnvironment("FIO_TEST_TOKEN", name -> name.equals("FIO_TEST_TOKEN") ? SECRET + "\n" : null))
                .isEqualTo(FioToken.of(SECRET));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FIO_API_TEST_TOKEN", matches = ".+")
    void should_read_from_process_environment_when_asked_given_variable_set_by_the_build() {
        assertThat(FioToken.fromEnvironment("FIO_API_TEST_TOKEN")).isEqualTo(FioToken.of("token-set-by-the-build-for-tests"));
    }

    @Test
    void should_reject_naming_the_variable_when_read_from_environment_given_unset_variable() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FioToken.fromEnvironment("FIO_TEST_TOKEN", name -> null))
                .withMessageContaining("FIO_TEST_TOKEN");
    }

    @Test
    void should_reject_naming_the_variable_when_read_from_process_environment_given_unset_variable() {
        String unset = "FIO_TOKEN_THAT_IS_NOT_SET_" + System.nanoTime();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> FioToken.fromEnvironment(unset))
                .withMessageContaining(unset);
    }
}
