package io.github.bzumik1.fio;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.argumentSet;

final class DecimalBounds {

    private DecimalBounds() {
    }

    static Stream<Arguments> beyond() {
        return Stream.of(
                argumentSet("41 integer digits", "1".repeat(41)),
                argumentSet("41 integer digits with a sign and a fraction", "-" + "9".repeat(41) + ".5"),
                argumentSet("1001 fraction digits", "0." + "0".repeat(1000) + "1"),
                argumentSet("1001 fraction digits without an integer part", "." + "1".repeat(1001)));
    }

    static Stream<Arguments> within() {
        return Stream.of(
                argumentSet("40 integer digits", "9".repeat(40)),
                argumentSet("1000 fraction digits", "0." + "0".repeat(999) + "1"),
                argumentSet("1000 fraction digits without an integer part", "." + "5".repeat(1000)),
                argumentSet("40 integer and 1000 fraction digits with a sign", "-" + "9".repeat(40) + "." + "1".repeat(1000)));
    }
}
