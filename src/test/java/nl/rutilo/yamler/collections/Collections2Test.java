package nl.rutilo.yamler.collections;

import nl.rutilo.yamler.testutils.IsMatcher;
import nl.rutilo.yamler.utils.Tuple;
import nl.rutilo.yamler.yamler.Yamler;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;

class Collections2Test {

    @SuppressWarnings("unchecked")
    private static <T> List<T> parse(String yaml) {
        return (List<T>)new Yamler().parseYaml(yaml).first();
    }

    @Test void concat() {
        final List<String> empty = Collections.emptyList();
        final List<String> a   = parse("[a,aa,aaa]");
        final List<String> b   = parse("[b,bb,bbb]");
        final List<String> c   = parse("[c,cc,ccc]");
        final List<String> ab  = parse("[a,aa,aaa,b,bb,bbb]");
        final List<String> abc = parse("[a,aa,aaa,b,bb,bbb,c,cc,ccc]");

        // happy flows
        MatcherAssert.assertThat(Collections2.concat(a,b), IsMatcher.is(ab));
        MatcherAssert.assertThat(Collections2.concat(a,b,c), IsMatcher.is(abc));

        // sad flows
        MatcherAssert.assertThat(Collections2.concat((List<String>[])null), IsMatcher.is(empty));
        MatcherAssert.assertThat(Collections2.concat((List<String>)null), IsMatcher.is(empty));
        MatcherAssert.assertThat(Collections2.concat(), IsMatcher.is(empty));
        MatcherAssert.assertThat(Collections2.concat(null, null), IsMatcher.is(empty));
        MatcherAssert.assertThat(Collections2.concat(a, null), IsMatcher.is(a));
        MatcherAssert.assertThat(Collections2.concat(null, a), IsMatcher.is(a));
        MatcherAssert.assertThat(Collections2.concat(empty, empty), IsMatcher.is(empty));
        MatcherAssert.assertThat(Collections2.concat(empty, empty, a, empty), IsMatcher.is(a));
        MatcherAssert.assertThat(Collections2.concat(null, a, null, b, null, c, null), IsMatcher.is(abc));
        MatcherAssert.assertThat(Collections2.concat(empty, a, empty, b, null, c, empty), IsMatcher.is(abc));
    }
    @Test void split() {
        MatcherAssert.assertThat(Collections2.split(parse("[1,2,3]"), 0), IsMatcher.is(parse("[]")));
        MatcherAssert.assertThat(Collections2.split(parse("[1,2,3]"), 1), IsMatcher.is(parse("[[1],[2],[3]]")));
        MatcherAssert.assertThat(Collections2.split(parse("[1,2,3,4,5,6]"), 2), IsMatcher.is(parse("[[1,2],[3,4],[5,6]]")));
        MatcherAssert.assertThat(Collections2.split(parse("[1,2,3,4,5,6,7]"), 2), IsMatcher.is(parse("[[1,2],[3,4],[5,6]]")));
        MatcherAssert.assertThat(Collections2.split(parse("[1,2,3,4,5,6,7,8,9]"), 3), IsMatcher.is(parse("[[1,2,3],[4,5,6],[7,8,9]]")));
        MatcherAssert.assertThat(Collections2.split(parse("[1,2,3,4,5,6,7,8,9]"), 4), IsMatcher.is(parse("[[1,2,3,4],[5,6,7,8]]")));
        MatcherAssert.assertThat(Collections2.split(parse("[1,2,3]"), 5), IsMatcher.is(parse("[]")));
    }
    @Test void map() {
        MatcherAssert.assertThat(
            Collections2.map(parse("[a,aa,aaa]"), parse("[b,bb,bbb]"),
              (a, b) -> a + "#" + b),
            IsMatcher.is(parse("[a#b,aa#bb,aaa#bbb]")));

        MatcherAssert.assertThat(
            Collections2.map(parse("[a,aa,aaa]"), parse("[b,bb]"),
              (a, b) -> a + "#" + b),
            IsMatcher.is(parse("[a#b,aa#bb,aaa#null]")));

        MatcherAssert.assertThat(
            Collections2.map(parse("[a,aa]"), parse("[b,bb,bbb]"),
              (a, b) -> a + "#" + b),
            IsMatcher.is(parse("[a#b,aa#bb,null#bbb]")));
    }
    @Test void firstOf() {
        assertThat(Collections2.firstOf(List.of()).isPresent(), is(false));
        assertThat(Collections2.firstOf(List.of("a", "b", "c")).orElse(""), is("a"));
        assertThat(Collections2.firstOf(new LinkedHashSet<>(List.of("a", "b", "c"))).orElse(""), is("a"));
        assertThat(Collections2.firstOf(Set.of()).isPresent(), is(false));
    }
    @Test void lastOf() {
        assertThat(Collections2.lastOf(List.of()).isPresent(), is(false));
        assertThat(Collections2.lastOf(List.of("a", "b", "c")).orElse(""), is("c"));
        assertThat(Collections2.lastOf(new LinkedHashSet<>(List.of("a", "b", "c"))).orElse(""), is("c"));
        assertThat(Collections2.lastOf(Set.of()).isPresent(), is(false));
    }
    @Test void toCollection() {
        assertThat(Collections2.toCollection(null), is(Collections.emptyList()));
        assertThat(Collections2.toCollection(List.of("a","b")), is(List.of("a","b")));
        assertThat(Collections2.toCollection("a"), is(List.of("a")));
        assertThat(Collections2.toCollection(1), is(List.of(1)));
    }
    @Test void toMap() {
        assertThat(Collections2.toMap(null), is(Collections.emptyMap()));
        assertThat(Collections2.toMap(Map.of("a",1)), is(Map.of("a",1)));
        assertThat(Collections2.toMap(List.of(Tuple.of("a",1),Tuple.of("b",2))), is(Map.of("a",1,"b",2)));
        assertThat(Collections2.toMap(List.of(Collections2.toMapEntry("a",1), Collections2.toMapEntry("b",2))), is(Map.of("a",1,"b",2)));
        assertThat(Collections2.toMap(new Tuple.Tuple2[] { Tuple.of("a",1), Tuple.of("b",2) }), is(Map.of("a",1,"b",2)));
        assertThat(Collections2.toMap("a"), is(Map.of("a","a")));
    }
    @Test void toMapEntry() {
        Map.Entry<String,String> abEntry = Collections2.toMapEntry("a","b");
        assertThat(abEntry.getKey(), is("a"));
        assertThat(abEntry.getValue(), is("b"));
        assertThat(Collections2.toMapEntry(abEntry), is(abEntry));
        assertThat(Collections2.toMapEntry(Tuple.of("a","b")), is(abEntry));
        assertThat(Collections2.toMapEntry(new String[] { "a","b" }), is(abEntry));
        assertThat(Collections2.toMapEntry(List.of("a","b")), is(abEntry));
        assertThat(Collections2.toMapEntry("a"), is(Collections2.toMapEntry("a", "a")));
    }
}
