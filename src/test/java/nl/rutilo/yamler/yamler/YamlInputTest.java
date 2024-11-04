package nl.rutilo.yamler.yamler;

import org.junit.jupiter.api.Test;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlInputTest {

    @Test void testReadUntil_newline() {
        final YamlInput input = new YamlInput("abc\ndef".toCharArray());
        assertThat(input.readUntil('\n'), is("abc"));
        assertThat(input.c(), is((int)'\n'));
    }
    @Test void testReadUntil_text() {
        final YamlInput input = new YamlInput("abcdef".toCharArray());
        assertThat(input.readUntil('c', 'd', 'e'), is("ab"));
        assertThat((char)input.c(), is('c'));
    }
    @Test void testReadUntil_textAtEnd() {
        final YamlInput input = new YamlInput("abcdef".toCharArray());
        assertThat(input.readUntil('d', 'e', 'f'), is("abc"));
        assertThat((char)input.c(), is('d'));
    }
    @Test void testReadUntil_textAtEndNotFound() {
        final YamlInput input = new YamlInput("abcdef".toCharArray());
        assertThat(input.readUntil('e', 'f', 'g'), is(""));
        assertTrue(input.ended());
    }
    @Test void testReadUntil_immediate() {
        final YamlInput input = new YamlInput("abcdef".toCharArray());
        assertThat(input.readUntil('a','b','c'), is(""));
        assertThat((char)input.c(), is('a'));
    }
}
