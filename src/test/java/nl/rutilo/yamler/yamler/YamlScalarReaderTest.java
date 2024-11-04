package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.utils.Value;
import org.junit.jupiter.api.Test;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static nl.rutilo.yamler.yamler.BaseYamlTest.mapOf;
import static org.hamcrest.CoreMatchers.nullValue;

class YamlScalarReaderTest {
    private String readString(String input) {
        return new YamlScalarReader(input.replace("`","\"").toCharArray()).readString().orElse("");
    }
    private Value<?> readScalar(String input) {
        return new YamlScalarReader(input.replace("`","\"").toCharArray()).readScalar();
    }

    @Test void testString() {
        assertThat(readString("`abc`"), is("abc"));
        assertThat(readString("`abc"), is("abc"));
        assertThat(readString("`abc\ndef`"), is("abc def"));
    }

    @Test void testEscapes() {
        assertThat(readString("`\\n`"), is("\n"));

        final String input = "`\\\\\\\"\\n\\r\\t\\b\\f\\0\\a\\v\\e\\u0020\\N\\_\\L\\P\\x9A\\uCAFE`";
        final String exp   = "\\\"\n\r\t\b\f\0\u0007\u000B\u001B\u0020\u0085\u00A0\u2028\u2029\u009A\uCAFE";
        assertThat(readString(input), is(exp));
    }

    @Test void testNumber() {
        assertThat(readScalar("123").get(), is(123));
        assertThat(readScalar("123.45").get(), is(123.45d));
    }

    @Test void testConstants() {
        assertThat(readScalar("true").get(), is(true));
        assertThat(readScalar("false").get(), is(false));
        assertThat(readScalar("null").get(), nullValue());
        assertThat(readScalar("undefined").get(), nullValue());
        assertThat(readScalar(".inf").get(), is(Double.POSITIVE_INFINITY));
        assertThat(readScalar("-.inf").get(), is(Double.NEGATIVE_INFINITY));
        assertThat(readScalar(".nan").get(), is(Double.NaN));
        assertThat(readScalar(".NaN").get(), is(Double.NaN));
        assertThat(readScalar(".nana").get(), is(".nana"));
    }

    @Test void testFlowBlockLiteral() {
        assertThat(readScalar(replaceSpecials(
              "|\n"
            + "..aaa\n"
            + "..bbb\n"
            + "...\n"
            + "..ccc\n"
            + "..ddd\n"
            + ".\n"
            + "..eee\n"
            + "..fff\n"
            + "end"
        )).get(), is("aaa\nbbb\n \nccc\nddd\n\neee\nfff\n"));
        assertThat(readScalar(replaceSpecials(
              "|1\n"
            + "..aaa\n"
            + "end"
        )).get(), is(" aaa\n"));
        assertThat(readScalar(replaceSpecials(
              "|\n"
            + "..'abc'\n"
            + "..def\n"
            + "..ghi\n"
            + "end"
        )).get(), is("'abc'\ndef\nghi\n"));
        assertThat(readScalar(replaceSpecials(
              "|+\n"
            + "\n"
            + "\n"
            + "end"
        )).get(), is("\n\n"));
        assertThat(readScalar(replaceSpecials(
              "explicit key # Empty value↓°"
        )).get(), is("explicit key"));
        assertThat(readScalar(replaceSpecials(
              "  |\n"
            + "  block key\n"
            + ": - item"
        )).get(), is("block key\n"));
    }
    @Test void testFlowBlockFolding() {
        assertThat(readScalar(replaceSpecials(
              ">\n"
            + "..aaa bbb\n"
            + "..ccc ddd\n"
            + ".\n"
            + "..eee\n"
            + "..fff\n"
            + "\n"
            + "..ggg\n"
            + "end"
        )).get(), is("aaa bbb ccc ddd\neee fff\nggg\n"));
        assertThat(readScalar(replaceSpecials(
              ">\n"
            + ".\n"
            + "..\n"
            + "..# aaa\n"
            + "end"
        )).get(), is("\n\n# aaa\n"));
        assertThat(readScalar(replaceSpecials(
              ">\n"
            + ".\t\n"
            + ".aaa\n"
            + "end"
        )).get(), is("\t aaa\n"));
        assertThat(readScalar(replaceSpecials(
              ">\n"
            + "..folded\n"
            + "..line\n"
            + "..\n"
            + "..next line\n"
            + "....* bullet\n"
            + "\n"
            + "....* list\n"
            + "....* lines\n"
            + "\n"
            + "..last\n"
            + "..line\n"
            + "..\n"
            + "# comment"
            + "end"
        )).get(), is("folded line\nnext line\n  * bullet\n\n  * list\n  * lines\n\nlast line\n"));
        assertThat(readScalar(replaceSpecials(
              ">\n"
            + "  'abc'\n"
            + "  def\n"
            + "\n"
            + "\n"
            + "\n"
            + "  ghi\n"
            + "\n"
            + "foo: bar}\n"
        )).get(), is("'abc' def\n\n\nghi\n"));
        assertThat(readScalar(replaceSpecials(
              ">\n"
            + " aaa\n"
            + " bbb\n"
            + "\n"
            + "   111\n"
            + "   222\n"
            + "\n"
            + " ddd\n"
        )).get(), is("aaa bbb\n\n  111\n  222\n\nddd\n"));
        assertThat(readScalar(replaceSpecials(
              ">\n"
            + "··foo·\n"
            + "·\n"
            + "··\t·bar\n"
            + "\n"
            + "··baz\n"
        )).get(), is("foo \n\n\t bar\n\nbaz\n"));
    }
    @Test void testBasicBlock() {
        assertThat(new YamlParser(replaceSpecials(
              "plain: newlines\n"
            + " will be\n"
            + "  removed\n"
            + "plain2:\n"
            + " here also newlines will\n"
            + "  be removed\n"
        )).parse().first(), is(mapOf(
            "plain", "newlines will be removed",
            "plain2", "here also newlines will be removed"
        )));
    }

    private String replaceSpecials(String s) {
        return s.replace(".", " ").replace("·", " ").replace("°", "");
    }

    // These tests focus on json scalars correctness.
    // Other functionality is tested in test-yaml-fragments.txt resource
}