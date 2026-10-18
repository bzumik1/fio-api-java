package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BeneficiaryTest {

    @Test
    void should_know_only_the_name_when_created_given_only_a_name() {
        assertThat(Beneficiary.named(" Hans Gruber ")).isEqualTo(new Beneficiary("Hans Gruber", null, null, null));
    }

    @Test
    void should_strip_and_normalise_when_created_given_padded_address() {
        assertThat(new Beneficiary("Amir Khan", " Nishtar Rd 13 ", " Karachi ", " pk "))
                .isEqualTo(new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK"));
    }

    @Test
    void should_drop_blank_address_parts_when_created_given_blank_street_and_city() {
        assertThat(new Beneficiary("Amir Khan", " ", "", null)).isEqualTo(Beneficiary.named("Amir Khan"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "Nishtar Rd 13 | Karachi | PK | true",
            "              | Karachi | PK | false",
            "Nishtar Rd 13 |         | PK | false",
            "Nishtar Rd 13 | Karachi |    | false"
    }, delimiter = '|')
    void should_tell_whether_address_is_complete_when_asked_given_address_parts(String street, String city, String country, boolean full) {
        assertThat(new Beneficiary("Amir Khan", street, city, country).hasFullAddress()).isEqualTo(full);
    }

    @Test
    void should_reject_when_created_given_blank_name() {
        assertThatIllegalArgumentException().isThrownBy(() -> Beneficiary.named(" "))
                .withMessage("The beneficiary's name is blank");
    }

    @Test
    void should_reject_when_created_with_address_given_blank_name() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Beneficiary("", "Gugitzgasse 2", "Wien", "AT"))
                .withMessage("The beneficiary's name is blank");
    }

    @Test
    void should_keep_name_when_created_given_name_of_50_characters() {
        assertThat(Beneficiary.named("x".repeat(50)).name()).hasSize(50);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "name   | The beneficiary's name is at most 50 characters, not 51",
            "street | The beneficiary's street is at most 50 characters, not 51",
            "city   | The beneficiary's city is at most 50 characters, not 51"
    }, delimiter = '|')
    void should_reject_naming_the_part_when_created_given_part_longer_than_50_characters(String part, String message) {
        String tooLong = "x".repeat(51);

        assertThatIllegalArgumentException().isThrownBy(() -> new Beneficiary(
                        part.equals("name") ? tooLong : "Amir",
                        part.equals("street") ? tooLong : null,
                        part.equals("city") ? tooLong : null,
                        null))
                .withMessage(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pakistan", "TCH", "PAK", "XX"})
    void should_reject_when_created_given_country_outside_fio_list(String country) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Beneficiary("Amir", null, null, country))
                .withMessage("Not a country code Fio imports: '" + country + "'");
    }

    @Test
    void should_reject_when_created_given_name_with_character_xml_cannot_carry() {
        assertThatIllegalArgumentException().isThrownBy(() -> Beneficiary.named("Amir\u0000"))
                .withMessage("The beneficiary's name holds a character the import format cannot carry");
    }
}
