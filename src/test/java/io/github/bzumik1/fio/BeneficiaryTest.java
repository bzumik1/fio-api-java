package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BeneficiaryTest {

    @Test
    void should_know_only_the_name_when_created_by_name() {
        assertThat(Beneficiary.named(" Hans Gruber ")).isEqualTo(new Beneficiary("Hans Gruber", null, null, null));
    }

    @Test
    void should_strip_and_normalise_when_created_given_padded_address() {
        assertThat(new Beneficiary("Amir Khan", " Nishtar Rd 13 ", " Karachi ", " pk "))
                .isEqualTo(new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK"));
        assertThat(new Beneficiary("Amir Khan", " ", "", null)).isEqualTo(Beneficiary.named("Amir Khan"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "Nishtar Rd 13 | Karachi | PK | true",
            "              | Karachi | PK | false",
            "Nishtar Rd 13 |         | PK | false",
            "Nishtar Rd 13 | Karachi |    | false"
    }, delimiter = '|')
    void should_tell_whether_address_is_complete_when_asked(String street, String city, String country, boolean full) {
        assertThat(new Beneficiary("Amir Khan", street, city, country).hasFullAddress()).isEqualTo(full);
    }

    @Test
    void should_reject_naming_the_part_when_created_given_invalid_part() {
        assertThatIllegalArgumentException().isThrownBy(() -> Beneficiary.named(" "))
                .withMessage("The beneficiary's name is blank");
        assertThatIllegalArgumentException().isThrownBy(() -> Beneficiary.named("x".repeat(36)))
                .withMessage("The beneficiary's name is at most 35 characters, not 36");
        assertThatIllegalArgumentException().isThrownBy(() -> new Beneficiary("Amir", "x".repeat(36), null, null))
                .withMessageContaining("street");
        assertThatIllegalArgumentException().isThrownBy(() -> new Beneficiary("Amir", null, "x".repeat(36), null))
                .withMessageContaining("city");
        assertThatIllegalArgumentException().isThrownBy(() -> new Beneficiary("Amir", null, null, "Pakistan"))
                .withMessage("Not a country code: 'Pakistan'");
    }
}
