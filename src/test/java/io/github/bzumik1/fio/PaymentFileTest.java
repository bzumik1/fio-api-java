package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PaymentFileTest {

    @Test
    void should_keep_own_copy_when_created_and_read_given_array_changed_afterwards() {
        byte[] content = {1, 2, 3};
        PaymentFile file = new PaymentFile(PaymentFileType.ABO, content);

        content[0] = 9;
        file.content()[1] = 9;

        assertThat(file.content()).containsExactly(1, 2, 3);
    }

    @Test
    void should_accept_up_to_two_megabytes_when_created() {
        assertThat(new PaymentFile(PaymentFileType.FIO_XML, new byte[PaymentFile.MAXIMUM_SIZE]).content())
                .hasSize(2 * 1024 * 1024);
    }

    @Test
    void should_reject_when_created_given_empty_or_too_large_file() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PaymentFile(PaymentFileType.ABO, new byte[0]))
                .withMessage("A payment file is not empty");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PaymentFile(PaymentFileType.ABO, new byte[PaymentFile.MAXIMUM_SIZE + 1]))
                .withMessage("A payment file is at most 2097152 bytes, not 2097153");
    }

    @Test
    void should_compare_by_type_and_content_when_compared() {
        PaymentFile file = new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2});

        assertThat(file)
                .isEqualTo(new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2}))
                .hasSameHashCodeAs(new PaymentFile(PaymentFileType.ABO, new byte[] {1, 2}))
                .isNotEqualTo(new PaymentFile(PaymentFileType.FIO_XML, new byte[] {1, 2}))
                .isNotEqualTo(new PaymentFile(PaymentFileType.ABO, new byte[] {1, 3}))
                .isNotEqualTo("PaymentFile")
                .isNotEqualTo(null)
                .hasToString("PaymentFile[ABO, 2 bytes]");
    }

    @ParameterizedTest
    @CsvSource({"ABO, abo, abo", "FIO_XML, xml, xml", "PAIN_001, pain001_xml, xml", "PAIN_008, pain008_xml, xml"})
    void should_name_upload_type_and_extension_when_asked_given_file_type(PaymentFileType type, String code, String extension) {
        assertThat(type.code()).isEqualTo(code);
        assertThat(type.extension()).isEqualTo(extension);
    }
}
