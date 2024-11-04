package nl.rutilo.yamler.utils;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConvertersTest {

    @Test void toInt() {
        Dimension dim = new Dimension(1,2);
        assertThat(Converters.toInt(null), is(0));
        assertThat(Converters.toInt(123L), is(123));
        assertThat(Converters.toInt("123"), is(123));
        assertThrows(NumberFormatException.class, () -> Converters.toInt("a"));
        assertThrows(IllegalArgumentException.class, () -> Converters.toInt(dim));
    }
    @Test void toDouble() {
        Dimension dim = new Dimension(1,2);
        assertThat(Converters.toDouble(null), is(0d));
        assertThat(Converters.toDouble(123L), is(123d));
        assertThat(Converters.toDouble("123"), is(123d));
        assertThrows(NumberFormatException.class, () -> Converters.toDouble("a"));
        assertThrows(IllegalArgumentException.class, () -> Converters.toDouble(dim));
    }
}