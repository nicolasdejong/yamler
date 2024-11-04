package nl.rutilo.yamler.utils;

import nl.rutilo.yamler.testutils.IsMatcher;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static nl.rutilo.yamler.testutils.SilverMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.fail;

class StringUtilsTest {

    @Test void testReplaceMatches() {
        assertThat(StringUtils.replaceMatches("abcdef", Pattern.compile("b(cd)e"), mat -> "@" + mat.group(1).toUpperCase() + "@"), is("a@CD@f"));
        assertThat(StringUtils.replaceMatches("ab!def", Pattern.compile("b(cd)e"), mat -> { fail("should not match"); return ""; }), is("ab!def"));
    }
    @Test void testReplacePattern() {
        assertThat(StringUtils.replacePattern("abcdef", Pattern.compile("bcde"), s -> s + "@"), is("abcde@f"));
        assertThat(StringUtils.replacePattern("abcdef", Pattern.compile("b(cd)e"), s -> s + "@"), is("abcde@f"));
    }
    @Test void testReplaceRegex() {
        assertThat(StringUtils.replaceRegex("abcdef", "b.d", g -> g[0].toUpperCase()), is("aBCDef"));
        assertThat(StringUtils.replaceRegex("abcdefghijklmn", "cd.+hij", g -> g[0].toUpperCase()), is("abCDEFGHIJklmn"));
        assertThat(StringUtils.replaceRegex("abcdefghijklmn", "cd(.+)hij", g -> g[1].toUpperCase()), is("abEFGklmn"));
        assertThat(StringUtils.replaceRegex("abcdef", "b(.)d", g -> g[1].toUpperCase()), is("aCef"));
    }
    @Test void testReplaceGroups() {
        assertThat(StringUtils.replaceGroups("a=1 b=2 c=3 d=4 e=5", "\\w=(.) \\w=(.)", "11", "22", "33", "44"), is("a=11 b=22 c=33 d=44 e=5"));
        assertThat(StringUtils.replaceGroups("a=1 b=2 c=3 d=4 e=5", "\\w=(.) \\w=(.)", "11", "22", null, "44"), is("a=11 b=22 c= d=44 e=5"));
        assertThat(StringUtils.replaceGroups("a=1 b=2 c=3 d=4 e=5", "\\w=(.) \\w=(.)", "11", "22"), is("a=11 b=22 c=11 d=22 e=5"));
        assertThat(StringUtils.replaceGroups("a=1 b=2 c=3 d=4 e=5", "\\w=(.) \\w=(.)", "11"), is("a=11 b=11 c=11 d=11 e=5"));
        assertThat(StringUtils.replaceGroups("a=1 b=2 c=3 d=4 e=5", "\\w=(.) \\w=(.)"), is("a= b= c= d= e=5"));
        assertThat(StringUtils.replaceGroups("a=1 b=2 c=3 d=4 e=5", "\\w=(.)", "X"), is("a=X b=X c=X d=X e=X"));
        assertThat(StringUtils.replaceGroups("a=1 b=2 c=3 d=4 e=5", "\\w=(?:.) \\w=(.)", "X"), is("a=1 b=X c=3 d=X e=5"));
    }
    @Test void testGetRegexMatch() {
        assertThat(StringUtils.getRegexMatch("abcdef", "ZZ"), is(Value.absent()));
        assertThat(StringUtils.getRegexMatch("abcdef", "c.e"), is(Value.of("cde")));
    }
    @Test void testGetRegexGroup() {
        assertThat(StringUtils.getRegexGroup("abcdef", "ab(ZZ)ef"), is(Value.absent()));
        assertThat(StringUtils.getRegexGroup("abcdef", "ab(c.e)f"), is(Value.of("cde")));
    }
    @Test void testGetRegexGroups() {
        assertThat(StringUtils.getRegexGroups("abcdef12345", "\\d\\d"), is(List.of("12", "34")));
        assertThat(StringUtils.getRegexGroups("abcdef12345", "b(.+)12(.+)5"), is(List.of("cdef", "34")));
    }
    @Test void countNewlinesInShouldCount() {
        assertThat(StringUtils.countNewlinesIn(null), is(0));
        assertThat(StringUtils.countNewlinesIn(""), is(0));
        assertThat(StringUtils.countNewlinesIn("abc"), is(0));
        assertThat(StringUtils.countNewlinesIn("abc\ndef\nghi"), is(2));
        assertThat(StringUtils.countNewlinesIn("\ndef\n"), is(2));
        assertThat(StringUtils.countNewlinesIn("abc\r\ndef\r\nghi"), is(2));
        assertThat(StringUtils.countNewlinesIn("\r\ndef\r\n"), is(2));
        assertThat(StringUtils.countNewlinesIn("abc\rdef\rghi"), is(2));
        assertThat(StringUtils.countNewlinesIn("\rdef\r"), is(2));
    }
    @Test void shortenPackageNameShouldGetEverShorter() {
        String pak = "nl.rutilo.yamler.utils.StringUtils";
        assertThat((pak = StringUtils.shortenPackageName(pak)), is("n.rutilo.yamler.utils.StringUtils"));
        assertThat((pak = StringUtils.shortenPackageName(pak)), is("n.r.yamler.utils.StringUtils"));
        assertThat((pak = StringUtils.shortenPackageName(pak)), is("n.r.y.utils.StringUtils"));
        assertThat((pak = StringUtils.shortenPackageName(pak)), is("n.r.y.u.StringUtils"));
        assertThat((      StringUtils.shortenPackageName(pak)), is("StringUtils"));

        pak = "nl.rutilo.yamler.utils";
        assertThat((pak = StringUtils.shortenPackageName(pak)), is("n.rutilo.yamler.utils"));
        assertThat((pak = StringUtils.shortenPackageName(pak)), is("n.r.yamler.utils"));
        assertThat((pak = StringUtils.shortenPackageName(pak)), is("n.r.y.utils"));
        assertThat((pak = StringUtils.shortenPackageName(pak)), is("n.r.y.u"));
        assertThat((      StringUtils.shortenPackageName(pak)), is("n.r.y.u"));

        assertThat(StringUtils.shortenPackageName(null), is(nullValue()));
        assertThat(StringUtils.shortenPackageName(""), is(""));
        assertThat(StringUtils.shortenPackageName("a"), is("a"));
        assertThat(StringUtils.shortenPackageName("a.b"), is("a.b"));

        assertThat(StringUtils.shortenPackageName("nl.rutilo.SomeClass.InnerClass"), is("n.rutilo.SomeClass.InnerClass"));
        assertThat(StringUtils.shortenPackageName("n.rutilo.SomeClass.InnerClass"), is("n.r.SomeClass.InnerClass"));
        assertThat(StringUtils.shortenPackageName("n.r.SomeClass.InnerClass"), is("SomeClass.InnerClass"));
    }
    @Test void allStringsShouldStartWithCommonPrefix() {
        assertThat(StringUtils.commonPrefix("abcdef", "abcd", "abcdefgh", "abc", "abcdefg"), is("abc"));
        assertThat(StringUtils.commonPrefix("abcdef", "", "abc"), is(""));
        assertThat(StringUtils.commonPrefix("ab.cd.ef", "ab.cd.e", "ab.cd.ef.gh").replaceAll("\\.[^.]+$",""), is("ab.cd"));
    }
    @Test void testPathPrefixes() {
        assertThat(StringUtils.getPathPrefixesOf("a"), is(Collections.emptyList()));
        assertThat(StringUtils.getPathPrefixesOf("a/b"), is(List.of("a")));
        assertThat(StringUtils.getPathPrefixesOf("a/b/c"), is(List.of("a", "a/b")));
        assertThat(StringUtils.getPathPrefixesOf("a/b/c/d"), is(List.of("a", "a/b", "a/b/c")));
        assertThat(StringUtils.getPathPrefixesOf("a/b/c/d/e"), is(List.of("a", "a/b", "a/b/c", "a/b/c/d")));
    }
    @Test void lc() {
        assertThat(StringUtils.lc("ABcDe"), IsMatcher.is("abcde"));
    }
    @Test void lcFirst() {
        assertThat(StringUtils.lcFirst("ABCDE"), IsMatcher.is("aBCDE"));
        assertThat(StringUtils.lcFirst(""), IsMatcher.is(""));
        assertThat(StringUtils.lcFirst("A"), IsMatcher.is("a"));
    }

    @Test void ucFirst() {
        assertThat(StringUtils.ucFirst("abcd"), IsMatcher.is("Abcd"));
        assertThat(StringUtils.ucFirst(""), IsMatcher.is(""));
        assertThat(StringUtils.ucFirst("a"), IsMatcher.is("A"));
    }
}