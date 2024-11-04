package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.testutils.IsMatcher;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static nl.rutilo.yamler.testutils.IsMatcher.is;

class YamlNumbersTest {

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void testNumberParsing() {
        // Numbers can be like: 123, -123, 1.234, .123, -.123, -0.12E-3, 0o223, 0x007BABE

        MatcherAssert.assertThat(YamlNumbers.toNumber("0").get(), IsMatcher.is(0));
        MatcherAssert.assertThat(YamlNumbers.toNumber("123").get(), IsMatcher.is(123));
        MatcherAssert.assertThat(YamlNumbers.toNumber("-123").get(), IsMatcher.is(-123));
        MatcherAssert.assertThat(YamlNumbers.toNumber("1.234").get(), IsMatcher.is(1.234));
        MatcherAssert.assertThat(YamlNumbers.toNumber(".123").get(), IsMatcher.is(.123));
        MatcherAssert.assertThat(YamlNumbers.toNumber("0.123").get(), IsMatcher.is(.123));
        MatcherAssert.assertThat(YamlNumbers.toNumber("-.123").get(), IsMatcher.is(-.123));
        MatcherAssert.assertThat(YamlNumbers.toNumber("-0.12E-3").get(), IsMatcher.is(-0.12E-3));
        MatcherAssert.assertThat(YamlNumbers.toNumber("0o223").get(), IsMatcher.is(147));
        MatcherAssert.assertThat(YamlNumbers.toNumber("010").get(), IsMatcher.is(8));
        MatcherAssert.assertThat(YamlNumbers.toNumber("0x0123").get(), IsMatcher.is(0x123));

        Assertions.assertTrue(YamlNumbers.toNumber("").isEmpty());
        Assertions.assertTrue(YamlNumbers.toNumber("123abc").isEmpty());
        Assertions.assertTrue(YamlNumbers.toNumber("2x3").isEmpty());
        Assertions.assertTrue(YamlNumbers.toNumber("12.").isEmpty());
        Assertions.assertTrue(YamlNumbers.toNumber(".").isEmpty());
        Assertions.assertTrue(YamlNumbers.toNumber("000.123").isEmpty());
        Assertions.assertTrue(YamlNumbers.toNumber("0009").isEmpty());
    }

}
