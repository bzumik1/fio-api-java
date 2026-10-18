package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PaymentImportPropertyTest {

    private static final long SEED = 20261018L;
    private static final int EXTRA_BATCHES = 40;
    private static final List<String> COUNTRIES = ImportSchema.enumeration("country_type");
    private static final List<String> PAYMENT_REASONS = ImportSchema.enumeration("payment_reason_type");
    private static final List<Currency> CURRENCIES = Stream.of(
                    "BHD", "CHF", "CLF", "CLP", "CZK", "EUR", "GBP", "HUF", "ISK", "JOD",
                    "JPY", "KRW", "KWD", "NOK", "PLN", "SEK", "TND", "USD", "XAU", "XXX")
            .map(Currency::getInstance)
            .collect(Collectors.toUnmodifiableList());
    private static final String[] TEXT_PIECES = {
            "a", "Z", "0", "9", " ", "č", "ř", "ž", "Ü", "ß", "&", "<", ">", "\"", "'", "\t", "\n", "\r", "-", "/",
            " ", "\u0085", "€", "😀", "]]>", "&amp;", "中"
    };
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int[] NUMBER_WEIGHTS = {6, 3, 7, 9, 10, 5, 8, 4, 2, 1};
    private static final int[] PREFIX_WEIGHTS = {10, 5, 8, 4, 2, 1};

    private static final PaymentImportWriter WRITER = new PaymentImportWriter();

    static Stream<Arguments> batches() {
        Random random = new Random(SEED);
        int count = Math.max(COUNTRIES.size(), PAYMENT_REASONS.size()) + EXTRA_BATCHES;
        List<Arguments> batches = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Generator generator = new Generator(random, index);
            batches.add(Arguments.of(index, List.of(generator.domestic(), generator.euro(), generator.foreign())));
        }
        return batches.stream();
    }

    @ParameterizedTest(name = "batch {0}")
    @MethodSource("batches")
    void should_match_fio_import_schema_when_written_given_any_orders_the_builders_accept(int index, List<PaymentOrder> orders) {
        byte[] xml = WRITER.write(orders);

        assertThatCode(() -> ImportSchema.validate(ImportSchema.IMPORT, xml)).doesNotThrowAnyException();
    }

    @Test
    void should_cover_every_country_reason_type_and_charge_when_generated_given_the_seeded_batches() {
        Set<String> countries = new HashSet<>();
        Set<String> reasons = new HashSet<>();
        Set<String> codes = new HashSet<>();
        Set<Integer> symbolLengths = new HashSet<>();
        batches().forEach(arguments -> {
            @SuppressWarnings("unchecked")
            List<PaymentOrder> orders = (List<PaymentOrder>) arguments.get()[1];
            DomesticPayment domestic = (DomesticPayment) orders.get(0);
            EuroPayment euro = (EuroPayment) orders.get(1);
            ForeignPayment foreign = (ForeignPayment) orders.get(2);
            countries.add(euro.beneficiary().country());
            countries.add(foreign.beneficiary().country());
            reasons.add(domestic.paymentReason());
            reasons.add(euro.paymentReason());
            reasons.add(foreign.paymentReason());
            codes.add(domestic.type().code());
            codes.add(euro.type().code());
            codes.add(foreign.charges().code());
            Stream.of(domestic.symbols().variable(), domestic.symbols().constant(), domestic.symbols().specific(),
                            euro.symbols().variable(), euro.symbols().constant(), euro.symbols().specific())
                    .filter(Objects::nonNull)
                    .forEach(symbol -> symbolLengths.add(symbol.length()));
        });
        countries.remove(null);
        reasons.remove(null);

        assertThat(countries).containsExactlyInAnyOrderElementsOf(COUNTRIES);
        assertThat(reasons).containsExactlyInAnyOrderElementsOf(PAYMENT_REASONS);
        assertThat(symbolLengths).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(codes).hasSize(DomesticPayment.Type.values().length + EuroPayment.Type.values().length
                + ForeignPayment.Charges.values().length);
    }

    private static final class Generator {

        private final Random random;
        private final int index;

        private Generator(Random random, int index) {
            this.random = random;
            this.index = index;
        }

        DomesticPayment domestic() {
            DomesticPayment.Builder builder = DomesticPayment.builder()
                    .from(payer())
                    .to(checksumValidAccount())
                    .amount(new Money(amount(), CURRENCIES.get(index % CURRENCIES.size())))
                    .date(date())
                    .type(DomesticPayment.Type.values()[index % DomesticPayment.Type.values().length]);
            symbol(10, 0).ifPresent(builder::variableSymbol);
            symbol(4, 1).ifPresent(builder::constantSymbol);
            symbol(10, 2).ifPresent(builder::specificSymbol);
            text(140).ifPresent(builder::messageForRecipient);
            text(140).ifPresent(builder::comment);
            if (random.nextBoolean()) {
                builder.paymentReason(PAYMENT_REASONS.get(index % PAYMENT_REASONS.size()));
            }
            return builder.build();
        }

        EuroPayment euro() {
            EuroPayment.Builder builder = EuroPayment.builder()
                    .from(payer())
                    .to(spaced(iban()))
                    .amount(new Money(amount(), Currency.getInstance("EUR")))
                    .date(date())
                    .beneficiary(new Beneficiary(requiredText(35), text(35).orElse(null), text(35).orElse(null),
                            cased(COUNTRIES.get(index % COUNTRIES.size()))))
                    .type(EuroPayment.Type.values()[index % EuroPayment.Type.values().length]);
            symbol(10, 3).ifPresent(builder::variableSymbol);
            symbol(4, 4).ifPresent(builder::constantSymbol);
            symbol(10, 5).ifPresent(builder::specificSymbol);
            if (random.nextBoolean()) {
                builder.bic(bic());
            }
            text(140).ifPresent(builder::comment);
            for (int line = random.nextInt(4); line > 0; line--) {
                builder.remittanceInfo(requiredText(35));
            }
            builder.paymentReason(PAYMENT_REASONS.get((index + 7) % PAYMENT_REASONS.size()));
            return builder.build();
        }

        ForeignPayment foreign() {
            ForeignPayment.Builder builder = ForeignPayment.builder()
                    .from(payer())
                    .to(random.nextBoolean() ? spaced(iban()) : alphanumeric(1 + random.nextInt(34)))
                    .bic(bic())
                    .amount(new Money(amount(), CURRENCIES.get((index * 7) % CURRENCIES.size())))
                    .date(date())
                    .beneficiary(new Beneficiary(requiredText(35), requiredText(35), requiredText(35),
                            COUNTRIES.get((COUNTRIES.size() - 1 - index % COUNTRIES.size()))))
                    .charges(ForeignPayment.Charges.values()[index % ForeignPayment.Charges.values().length])
                    .paymentReason(PAYMENT_REASONS.get((PAYMENT_REASONS.size() - 1 - index % PAYMENT_REASONS.size())));
            for (int line = 1 + random.nextInt(4); line > 0; line--) {
                builder.remittanceInfo(requiredText(35));
            }
            text(140).ifPresent(builder::comment);
            return builder.build();
        }

        private String payer() {
            String number = digits(1 + random.nextInt(10));
            return random.nextBoolean() ? number : number + "/2010";
        }

        private DomesticAccount checksumValidAccount() {
            String prefix = random.nextInt(3) == 0 ? null : checksumValid(1 + random.nextInt(6), PREFIX_WEIGHTS);
            return new DomesticAccount(prefix, checksumValid(1 + random.nextInt(10), NUMBER_WEIGHTS), digits(4));
        }

        private String checksumValid(int length, int[] weights) {
            while (true) {
                String head = digits(length - 1);
                int sum = 0;
                for (int position = 0; position < head.length(); position++) {
                    sum += Character.digit(head.charAt(position), 10) * weights[weights.length - length + position];
                }
                int last = (11 - sum % 11) % 11;
                if (last < 10) {
                    return head + last;
                }
            }
        }

        private BigDecimal amount() {
            switch (random.nextInt(6)) {
                case 0:
                    return new BigDecimal("9999999999999999.99");
                case 1:
                    return new BigDecimal("0.01");
                default:
                    BigDecimal amount = new BigDecimal(new BigInteger(digits(1 + random.nextInt(18))), random.nextInt(3));
                    return amount.signum() > 0 && amount.precision() - amount.scale() <= 16 ? amount : BigDecimal.ONE;
            }
        }

        private LocalDate date() {
            switch (random.nextInt(5)) {
                case 0:
                    return LocalDate.of(1, 1, 1);
                case 1:
                    return LocalDate.of(9999, 12, 31);
                default:
                    return LocalDate.ofEpochDay(random.nextInt(3_000_000) - 700_000);
            }
        }

        private Optional<String> symbol(int maximumDigits, int offset) {
            return (index + offset) % 7 == 6 ? Optional.empty() : Optional.of(digits(1 + (index + offset) % maximumDigits));
        }

        private Optional<String> text(int maximumLength) {
            return random.nextInt(4) == 0 ? Optional.empty() : Optional.of(requiredText(maximumLength));
        }

        private String requiredText(int maximumLength) {
            int length = random.nextBoolean() ? maximumLength : 1 + random.nextInt(maximumLength);
            StringBuilder text = new StringBuilder();
            while (text.length() < length) {
                String piece = TEXT_PIECES[random.nextInt(TEXT_PIECES.length)];
                if (text.length() + piece.length() <= length) {
                    text.append(piece);
                }
            }
            String stripped = text.toString().strip();
            return stripped.isEmpty() ? "x" : stripped;
        }

        private String iban() {
            String country = COUNTRIES.get(random.nextInt(COUNTRIES.size()));
            String bban = alphanumeric(1 + random.nextInt(30));
            StringBuilder numeric = new StringBuilder();
            for (char symbol : (bban + country + "00").toCharArray()) {
                numeric.append(Character.digit(symbol, 36));
            }
            int check = 98 - new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue();
            return country + String.format(Locale.ROOT, "%02d", check) + bban;
        }

        private String bic() {
            String bic = letters(6) + alphanumeric(2) + (random.nextBoolean() ? alphanumeric(3) : "");
            return random.nextBoolean() ? bic.toLowerCase(Locale.ROOT) : bic;
        }

        private String spaced(String identifier) {
            return random.nextBoolean() ? identifier.replaceAll("(.{4})", "$1 ").strip() : identifier;
        }

        private String cased(String code) {
            return random.nextBoolean() ? code : " " + code.toLowerCase(Locale.ROOT) + " ";
        }

        private String digits(int length) {
            StringBuilder digits = new StringBuilder();
            for (int position = 0; position < length; position++) {
                digits.append(random.nextInt(10));
            }
            return digits.toString();
        }

        private String letters(int length) {
            StringBuilder letters = new StringBuilder();
            for (int position = 0; position < length; position++) {
                letters.append((char) ('A' + random.nextInt(26)));
            }
            return letters.toString();
        }

        private String alphanumeric(int length) {
            StringBuilder text = new StringBuilder();
            for (int position = 0; position < length; position++) {
                text.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
            }
            return text.toString();
        }
    }
}
