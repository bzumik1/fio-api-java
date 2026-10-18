package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ParserFuzzTest {

    private static final long SEED = 20261018L;
    private static final int MUTANTS_PER_FIXTURE = 150;
    private static final Pattern VALUE = Pattern.compile("\"[^\"]*\"|>[^<]*<|-?\\d+(?:\\.\\d+)?");
    private static final String[] TRICKY_VALUES = {
            "", " ", "null", "-1", "0", "99999999999999999999", "1e999999999", "1E-999999999", "1e2147483648",
            "NaN", "-0.0", "\"\"", "&amp;", "&#0;", "&undefined;", "<x>", "</x>", "<![CDATA[", "﻿", "2012-13-45",
            "2012-02-30T25:61:61", "+0200", "Z", "9999999999999999999999", "{", "[", "]", "}", ",", ":", "\\u0000",
            "\\ud800", "true", "\u0000", "ÿþ", "<?xml version=\"1.0\" encoding=\"UTF-16\"?>",
            "<?xml version=\"1.0\" encoding=\"nonsense\"?>", "<!DOCTYPE x>", "xmlns:a=\"\"", "a:b", "id=\"7\""
    };

    private record Target(String name, Consumer<byte[]> parser, byte[] fixture) {
    }

    static Stream<Arguments> mutants() {
        Random random = new Random(SEED);
        List<Target> targets = List.of(
                json("periods statement", Fixtures.PERIODS_STATEMENT),
                json("documentation statement with epoch dates", Fixtures.DOCUMENTATION_EXAMPLE_WITH_EPOCH_DATES),
                json("documentation statement 2012/3", FixtureBytes.STATEMENT_2012_3),
                new Target("merchant documentation example", new MerchantStatementParser()::parse,
                        FixtureBytes.read(Fixtures.MERCHANT_DOCUMENTATION_EXAMPLE)),
                new Target("merchant schema example", new MerchantStatementParser()::parse,
                        FixtureBytes.read(Fixtures.MERCHANT_SCHEMA_EXAMPLE)),
                new Target("accepted import", new ImportResponseParser()::parse, FixtureBytes.read(Fixtures.IMPORT_ACCEPTED)),
                new Target("refused import", new ImportResponseParser()::parse, FixtureBytes.read(Fixtures.IMPORT_REFUSED)),
                new Target("statement number", bytes -> StatementId.parse(new String(bytes, StandardCharsets.UTF_8)),
                        " 2012,3\n".getBytes(StandardCharsets.UTF_8)));
        List<Arguments> mutants = new ArrayList<>();
        for (Target target : targets) {
            for (int index = 1; index <= MUTANTS_PER_FIXTURE; index++) {
                mutants.add(Arguments.of(target.name() + " #" + index, target.parser(), mutated(random, target.fixture())));
            }
        }
        return mutants.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutants")
    void should_parse_or_reject_as_malformed_when_parsed_given_mutated_fixture(
            String name, Consumer<byte[]> parser, byte[] input) {
        Throwable thrown = catchThrowable(() -> parser.accept(input));

        if (thrown != null) {
            assertThat(thrown).as(new String(input, StandardCharsets.ISO_8859_1)).isExactlyInstanceOf(MalformedResponseException.class);
        }
    }

    @Test
    void should_reach_both_parsed_and_rejected_bodies_when_mutated_given_each_fixture() {
        Map<String, Set<Boolean>> outcomes = new TreeMap<>();
        mutants().forEach(mutant -> {
            Object[] parts = mutant.get();
            @SuppressWarnings("unchecked")
            Consumer<byte[]> parser = (Consumer<byte[]>) parts[1];
            boolean parsed = catchThrowable(() -> parser.accept((byte[]) parts[2])) == null;
            outcomes.computeIfAbsent(((String) parts[0]).replaceFirst(" #\\d+$", ""), target -> new HashSet<>()).add(parsed);
        });

        assertThat(outcomes).hasSize(8).allSatisfy((target, seen) -> assertThat(seen).as(target).containsExactlyInAnyOrder(true, false));
    }

    private static Target json(String name, String fixture) {
        return new Target(name, new AccountStatementParser()::parse, FixtureBytes.read(fixture));
    }

    private static byte[] mutated(Random random, byte[] fixture) {
        String text = new String(fixture, StandardCharsets.ISO_8859_1);
        int mutations = 1 + random.nextInt(3);
        for (int mutation = 0; mutation < mutations; mutation++) {
            text = mutation(random, text);
        }
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String mutation(Random random, String text) {
        if (text.isEmpty()) {
            return randomBytes(random);
        }
        int at = random.nextInt(text.length());
        switch (random.nextInt(7)) {
            case 0:
                return text.substring(0, at) + text.substring(Math.min(text.length(), at + 1 + random.nextInt(16)));
            case 1:
                String copied = text.substring(at, Math.min(text.length(), at + 1 + random.nextInt(64)));
                int target = random.nextInt(text.length() + 1);
                return text.substring(0, target) + copied + text.substring(target);
            case 2:
                return swappedValues(random, text);
            case 3:
                return text.substring(0, at) + randomBytes(random) + text.substring(at);
            case 4:
                return text.substring(0, at);
            case 5:
                return text.substring(0, at) + (char) random.nextInt(256) + text.substring(at + 1);
            default:
                return replacedValue(random, text);
        }
    }

    private static String randomBytes(Random random) {
        StringBuilder bytes = new StringBuilder();
        int length = 1 + random.nextInt(8);
        for (int index = 0; index < length; index++) {
            bytes.append((char) random.nextInt(256));
        }
        return bytes.toString();
    }

    private static String swappedValues(Random random, String text) {
        List<int[]> values = values(text);
        if (values.size() < 2) {
            return text;
        }
        int[] first = values.get(random.nextInt(values.size()));
        int[] second = values.get(random.nextInt(values.size()));
        if (first[0] > second[0]) {
            int[] earlier = second;
            second = first;
            first = earlier;
        }
        if (first[1] > second[0]) {
            return text;
        }
        return text.substring(0, first[0]) + text.substring(second[0], second[1])
                + text.substring(first[1], second[0]) + text.substring(first[0], first[1]) + text.substring(second[1]);
    }

    private static String replacedValue(Random random, String text) {
        List<int[]> values = values(text);
        if (values.isEmpty()) {
            return text;
        }
        int[] value = values.get(random.nextInt(values.size()));
        String tricky = new String(TRICKY_VALUES[random.nextInt(TRICKY_VALUES.length)].getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        boolean keepDelimiters = value[1] - value[0] >= 2 && !Character.isDigit(text.charAt(value[0]))
                && text.charAt(value[0]) != '-';
        return keepDelimiters
                ? text.substring(0, value[0] + 1) + tricky + text.substring(value[1] - 1)
                : text.substring(0, value[0]) + tricky + text.substring(value[1]);
    }

    private static List<int[]> values(String text) {
        List<int[]> values = new ArrayList<>();
        Matcher matcher = VALUE.matcher(text);
        while (matcher.find()) {
            values.add(new int[] {matcher.start(), matcher.end()});
        }
        return values;
    }
}
