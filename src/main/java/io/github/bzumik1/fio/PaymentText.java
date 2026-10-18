package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

final class PaymentText {

    private static final String FIO_BANK_CODE = "2010";
    private static final int CENT_DECIMALS = 2;
    private static final int INTEGER_DIGITS = 16;
    private static final Pattern XML_INCOMPATIBLE = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uD800-\\uDFFF\\uFFFE\\uFFFF]");
    private static final int FIRST_YEAR = 1;
    private static final int LAST_YEAR = 9999;
    private static final Set<String> COUNTRY_CODES = Set.of((""
            + "AD AE AF AG AI AL AM AN AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS "
            + "BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG "
            + "EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR "
            + "HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR "
            + "LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG "
            + "NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE "
            + "SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TP TR TT TV TW TZ "
            + "UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS XK YE YT ZA ZM ZR ZW").split(" "));
    private static final Set<String> PAYMENT_REASONS = Set.of((""
            + "110 112 120 122 130 132 135 190 195 210 211 212 213 214 215 216 217 218 219 220 221 222 223 224 226 "
            + "233 235 239 260 262 265 270 272 275 280 282 285 295 310 311 312 315 318 320 325 326 327 328 330 332 "
            + "335 340 345 346 347 348 352 355 360 361 365 368 369 370 372 375 376 378 380 382 384 385 386 387 388 "
            + "390 392 395 397 410 412 510 520 530 532 535 538 540 550 610 612 615 618 620 622 625 628 630 632 635 "
            + "640 650 652 653 725 735 740 742 745 748 750 752 755 760 762 770 790 818 820 822 825 835 850 852 855 "
            + "862 890 950 952").split(" "));

    private PaymentText() {
    }

    static @Nullable String optional(@Nullable String value, int maximumLength, String name) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        if (stripped.length() > maximumLength) {
            throw new IllegalArgumentException(
                    "The " + name + " is at most " + maximumLength + " characters, not " + stripped.length());
        }
        if (XML_INCOMPATIBLE.matcher(stripped).find()) {
            throw new IllegalArgumentException("The " + name + " holds a character the import format cannot carry");
        }
        return stripped;
    }

    static String required(String value, int maximumLength, String name) {
        String stripped = optional(requireNonNull(value, name), maximumLength, name);
        if (stripped == null) {
            throw new IllegalArgumentException("The " + name + " is blank");
        }
        return stripped;
    }

    static <T> T required(@Nullable T value, String name) {
        if (value == null) {
            throw new IllegalStateException("The " + name + " is required");
        }
        return value;
    }

    static Money positive(@Nullable Money amount) {
        Money checked = required(amount, "amount");
        if (!checked.isPositive()) {
            throw new IllegalArgumentException("A payment amount is positive: " + checked);
        }
        if ((long) checked.amount().precision() - checked.amount().scale() > INTEGER_DIGITS) {
            throw new IllegalArgumentException("A payment amount has at most " + INTEGER_DIGITS + " digits before the decimal point: " + checked);
        }
        if (checked.amount().stripTrailingZeros().scale() > CENT_DECIMALS) {
            throw new IllegalArgumentException("A payment amount has at most two decimal places: " + checked);
        }
        return checked;
    }

    static DomesticAccount payerAccount(DomesticAccount account) {
        requireNonNull(account, "from");
        if (account.prefix() != null) {
            throw new IllegalArgumentException("The payer's account is a Fio account, which has no prefix: " + account);
        }
        if (account.bankCode() != null && !account.bankCode().equals(FIO_BANK_CODE)) {
            throw new IllegalArgumentException(
                    "The payer's account is a Fio account with bank code " + FIO_BANK_CODE + ": " + account);
        }
        return account;
    }

    static String bic(String value) {
        requireNonNull(value, "bic");
        String stripped = value.strip().toUpperCase(Locale.ROOT);
        if (!stripped.matches("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?")) {
            throw new IllegalArgumentException("Not a BIC: '" + value + "'");
        }
        return stripped;
    }

    static String iban(String value) {
        requireNonNull(value, "iban");
        String stripped = value.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!stripped.matches("[A-Z]{2}\\d{2}[A-Z0-9]{1,30}")) {
            throw new IllegalArgumentException("Not an IBAN: '" + value + "'");
        }
        if (!Iban.hasValidChecksum(stripped)) {
            throw new IllegalArgumentException("Not an IBAN, the check digits do not match: '" + value + "'");
        }
        return stripped;
    }

    static String countryCode(String value) {
        requireNonNull(value, "country");
        String stripped = value.strip().toUpperCase(Locale.ROOT);
        if (!COUNTRY_CODES.contains(stripped)) {
            throw new IllegalArgumentException("Not a country code Fio imports: '" + value + "'");
        }
        return stripped;
    }

    static @Nullable String paymentReason(@Nullable String value) {
        if (value != null && !PAYMENT_REASONS.contains(value)) {
            throw new IllegalArgumentException("Not a payment reason code Fio imports: '" + value + "'");
        }
        return value;
    }

    static LocalDate dueDate(LocalDate date) {
        requireNonNull(date, "date");
        if (date.getYear() < FIRST_YEAR || date.getYear() > LAST_YEAR) {
            throw new IllegalArgumentException("A due date has a four-digit year: " + date);
        }
        return date;
    }
}
