package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.bzumik1.fio.StubBank.SECRET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
    @EmptySource
    @ValueSource(strings = {"   ", "\n"})
    void should_reject_when_created_given_no_value(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> FioToken.of(value)).withMessageContaining("is required");
    }

    @Test
    void should_reject_with_parameter_name_when_created_given_null() {
        assertThatNullPointerException().isThrownBy(() -> FioToken.of(null)).withMessage("value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "abcdefghijklmno", " abcdefghijklmno "})
    void should_reject_without_echoing_value_when_created_given_fewer_than_sixteen_characters(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> FioToken.of(value))
                .withMessageContaining("at least 16")
                .withMessageNotContaining(value.strip());
    }

    @Test
    void should_accept_when_created_given_exactly_sixteen_characters() {
        assertThat(FioToken.of("abcdefghijklmnop")).hasToString("FioToken[***]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/x", " x", "?x=1", "%2F", ".x"})
    void should_reject_without_echoing_value_when_created_given_characters_unsafe_in_url_path(String unsafe) {
        String value = SECRET.substring(0, 20) + unsafe;

        assertThatIllegalArgumentException()
                .isThrownBy(() -> FioToken.of(value))
                .withMessageContaining("only letters, digits")
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
    void should_mask_the_beginning_of_the_token_when_masking_excerpt_given_token_cut_short_by_the_cut_mark() {
        FioToken token = FioToken.of(SECRET);

        assertThat(token.maskExcerpt("GET /last/" + SECRET.substring(0, 20) + "\u2026"))
                .isEqualTo("GET /last/***\u2026");
    }

    @Test
    void should_mask_whole_tokens_and_each_cut_beginning_when_masking_excerpt_given_several_cut_marks() {
        FioToken token = FioToken.of(SECRET);

        assertThat(token.maskExcerpt(SECRET + " '" + SECRET.substring(0, 12) + "\u2026' and '"
                + SECRET.substring(0, SECRET.length() - 1) + "\u2026' rest"))
                .isEqualTo("*** '***\u2026' and '***\u2026' rest");
    }

    @Test
    void should_mask_the_beginning_of_the_token_when_masking_excerpt_given_token_cut_short_by_another_library() {
        FioToken token = FioToken.of(SECRET);

        assertThat(token.maskExcerpt("Text 'x" + SECRET.substring(0, SECRET.length() - 1) + "...' could not be parsed"))
                .isEqualTo("Text 'x***...' could not be parsed");
    }

    @Test
    void should_mask_every_beginning_of_the_token_when_masking_excerpt_given_beginnings_anywhere_in_the_text() {
        FioToken token = FioToken.of(SECRET);

        assertThat(token.maskExcerpt(SECRET.substring(0, 12) + " and " + SECRET.substring(0, 30) + SECRET.substring(0, 20)))
                .isEqualTo("*** and ******");
    }

    @Test
    void should_keep_short_beginnings_when_masking_excerpt_given_fewer_than_twelve_characters_of_the_token() {
        FioToken token = FioToken.of(SECRET);
        String text = "'" + SECRET.substring(0, 11) + "\u2026' and " + SECRET.charAt(0) + "\u2026";

        assertThat(token.maskExcerpt(text)).isEqualTo(text);
        assertThat(token.occursIn(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain text", "\u2026 at the start", "cut after other text\u2026", "cut after the end of a token: x\u2026"})
    void should_leave_text_alone_when_masking_excerpt_given_no_beginning_of_the_token_before_a_cut_mark(String text) {
        assertThat(FioToken.of(SECRET).maskExcerpt(text)).isEqualTo(text);
    }

    @Test
    void should_find_value_when_asked_given_text_holding_only_a_cut_beginning_of_the_token() {
        assertThat(FioToken.of(SECRET).occursIn("value '" + SECRET.substring(0, 30) + "\u2026'")).isTrue();
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
