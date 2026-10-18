package io.github.bzumik1.fio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

final class PaymentSamples {

    static final LocalDate DUE = LocalDate.of(2026, 10, 20);
    static final String PAYER = "1234562";

    private PaymentSamples() {
    }

    static DomesticPayment.Builder domesticBuilder() {
        return DomesticPayment.builder()
                .from(PAYER)
                .to("2212-2000000699/0300")
                .amount(Money.czk("100.00"))
                .date(DUE);
    }

    static DomesticPayment domestic() {
        return domesticBuilder().build();
    }

    static DomesticPayment fullDomestic() {
        return domesticBuilder()
                .constantSymbol("0558")
                .variableSymbol("1234567890")
                .specificSymbol("0000000002")
                .messageForRecipient("Hračky pro děti v PENNY MARKET")
                .comment("Dárky & <hračky>")
                .paymentReason("110")
                .type(DomesticPayment.Type.PRIORITY)
                .build();
    }

    static EuroPayment.Builder euroBuilder() {
        return EuroPayment.builder()
                .from(PAYER)
                .to("AT611904300234573201")
                .amount(euro("100.00"))
                .date(DUE)
                .beneficiary("Hans Gruber");
    }

    static EuroPayment euro() {
        return euroBuilder().build();
    }

    static EuroPayment fullEuro() {
        return euroBuilder()
                .constantSymbol("0558")
                .variableSymbol("1234567890")
                .specificSymbol("1234567890")
                .bic("ABAGATWWXXX")
                .comment("Erste Zahlung")
                .beneficiary(new Beneficiary("Hans Gruber", "Gugitzgasse 2", "Wien", "AT"))
                .remittanceInfo("Rechnung 2026-17")
                .remittanceInfo("Kunde 4711")
                .remittanceInfo("Danke")
                .paymentReason("110")
                .type(EuroPayment.Type.INSTANT)
                .build();
    }

    static ForeignPayment.Builder foreignBuilder() {
        return ForeignPayment.builder()
                .from(PAYER)
                .to("PK36SCBL0000001123456702")
                .bic("ALFHPKKAXXX")
                .amount(new Money(new BigDecimal("100"), Currency.getInstance("USD")))
                .date(DUE)
                .beneficiary(new Beneficiary("Amir Khan", "Nishtar Rd 13", "Karachi", "PK"))
                .remittanceInfo("Payment for hotel 032013")
                .paymentReason("348");
    }

    static ForeignPayment foreign() {
        return foreignBuilder().build();
    }

    static ForeignPayment fullForeign() {
        return foreignBuilder()
                .comment("Payment a0315")
                .remittanceInfo("Room 12")
                .remittanceInfo("Two nights")
                .remittanceInfo("Thank you")
                .charges(ForeignPayment.Charges.BENEFICIARY)
                .build();
    }

    static Money euro(String amount) {
        return Money.of(amount, "EUR");
    }
}
