package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PaymentFileTest {

    @Test
    void should_keep_own_copy_when_created_given_array_changed_afterwards() {
        byte[] content = {1, 2, 3};
        PaymentFile file = new PaymentFile(PaymentFileType.ABO, content);

        content[0] = 9;

        assertThat(file.content()).containsExactly(1, 2, 3);
    }

    @Test
    void should_hand_out_copies_when_content_read_given_file() {
        PaymentFile file = new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2, 3});

        file.content()[1] = 9;

        assertThat(file.content()).containsExactly(1, 2, 3);
    }

    @Test
    void should_accept_when_created_given_file_of_exactly_two_megabytes() {
        assertThat(new PaymentFile(PaymentFileType.FIO_XML, new byte[PaymentFile.MAXIMUM_SIZE]).content())
                .hasSize(2 * 1024 * 1024);
    }

    @Test
    void should_reject_when_created_given_empty_file() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PaymentFile(PaymentFileType.ABO, new byte[0]))
                .withMessage("A payment file is not empty");
    }

    @Test
    void should_reject_when_created_given_file_one_byte_over_two_megabytes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentFile(PaymentFileType.ABO, new byte[PaymentFile.MAXIMUM_SIZE + 1]))
                .withMessage("A payment file is at most 2097152 bytes, not 2097153");
    }

    @Test
    void should_equal_with_same_hash_when_compared_given_same_type_and_content() {
        PaymentFile file = new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2});
        PaymentFile same = new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2});

        assertThat(file).isEqualTo(same).hasSameHashCodeAs(same);
    }

    static Stream<Object> differentFiles() {
        return Stream.of(
                new PaymentFile(PaymentFileType.FIO_XML, new byte[] {1, 2}),
                new PaymentFile(PaymentFileType.ABO, new byte[] {1, 3}),
                "PaymentFile");
    }

    @ParameterizedTest
    @MethodSource("differentFiles")
    void should_differ_when_compared_given_other_type_content_or_class(Object other) {
        assertThat(new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2})).isNotEqualTo(other);
    }

    @Test
    void should_differ_when_compared_given_null() {
        assertThat(new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2}).equals(null)).isFalse();
    }

    @Test
    void should_print_type_and_size_when_printed_given_file() {
        assertThat(new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2})).hasToString("PaymentFile[ABO, 2 bytes]");
    }

    @ParameterizedTest
    @CsvSource({"ABO, abo, abo", "FIO_XML, xml, xml", "PAIN_001, pain001_xml, xml", "PAIN_008, pain008_xml, xml"})
    void should_name_upload_type_and_extension_when_asked_given_file_type(PaymentFileType type, String code, String extension) {
        assertThat(type).returns(code, PaymentFileType::code).returns(extension, PaymentFileType::extension);
    }
}
