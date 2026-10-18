package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadTest {

    private static final Charset WINDOWS_1250 = Charset.forName("windows-1250");

    @Test
    void should_keep_its_own_copy_when_created_given_array_changed_afterwards() {
        byte[] bytes = "ahoj".getBytes(StandardCharsets.UTF_8);
        Download document = new Download("text/plain", StandardCharsets.UTF_8, bytes);

        bytes[0] = 'x';

        assertThat(document.asText()).isEqualTo("ahoj");
    }

    @Test
    void should_hand_out_copies_when_content_read_given_document() {
        Download document = new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8));

        document.content()[0] = 'y';

        assertThat(document.asText()).isEqualTo("ahoj");
    }

    @Test
    void should_print_media_type_charset_and_size_when_printed_given_document() {
        assertThat(new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8)))
                .hasToString("Download[text/plain, UTF-8, 4 bytes]");
    }

    @Test
    void should_equal_with_same_hash_when_compared_given_same_media_type_charset_and_bytes() {
        Download document = new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8));
        Download same = new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8));

        assertThat(document).isEqualTo(same).hasSameHashCodeAs(same);
    }

    static Stream<Object> differentDocuments() {
        return Stream.of(
                new Download("text/csv", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8)),
                new Download("text/plain", StandardCharsets.UTF_8, "ahoi".getBytes(StandardCharsets.UTF_8)),
                new Download("text/plain", StandardCharsets.ISO_8859_1, "ahoj".getBytes(StandardCharsets.UTF_8)),
                "ahoj");
    }

    @ParameterizedTest
    @MethodSource("differentDocuments")
    void should_differ_when_compared_given_other_media_type_charset_bytes_or_type(Object other) {
        assertThat(new Download("text/plain", StandardCharsets.UTF_8, "ahoj".getBytes(StandardCharsets.UTF_8))).isNotEqualTo(other);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/csv; charset=windows-1250", "text/csv;charset=\"windows-1250\"", "text/csv; CHARSET=Windows-1250"})
    void should_decode_with_named_charset_when_read_given_media_type_naming_a_charset(String mediaType) {
        Download document = Download.of(mediaType, StandardCharsets.UTF_8, "Příjem".getBytes(WINDOWS_1250));

        assertThat(document).returns(WINDOWS_1250, Download::charset).returns("Příjem", Download::asText);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/csv", "text/csv;charset=nonsense-9"})
    void should_use_documented_charset_when_read_given_media_type_without_a_known_charset(String mediaType) {
        assertThat(Download.of(mediaType, WINDOWS_1250, new byte[0]).charset()).isEqualTo(WINDOWS_1250);
    }
}
