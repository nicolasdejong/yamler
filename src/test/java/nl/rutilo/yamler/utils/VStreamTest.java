package nl.rutilo.yamler.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({
  "squid:S5778" // allow multiple statements in assertThrows()
  , "ResultOfMethodCallIgnored" // stream needs to run sometimes without needing the result
})
class VStreamTest {
    private static final Class<IllegalStateException> EX_UNCHECKED_TYPE = IllegalStateException.class;
    private static void notSupposedToGetHere() {
        fail("Not supposed to get here");
    }
    private static void throwChecked() throws Exception { throw new IOException("checked"); }
    private static void throwUnchecked() throws RuntimeException { throw new IllegalStateException("unchecked"); }
    private static VStream<String> throwInStream(VStream<String> stream) {
        return stream.filter(n -> { throwChecked(); return true; });
    }
    private static VStream<String> streamThatHasThrown() {
        return throwInStream(VStream.of("a", "b", "c"));
    }
    private static <M> void assertValueHoldsCheckedException(Value<M> value) {
        value.ifPresent(r -> System.out.println("Unexpected present: " + r));
        assertThat(value.isPresent(), is(false));
        assertThat(value.isThrown(), is(true));
        assertThat(value.getThrown().map(Throwable::toString).orElse("?"), is(new IOException("checked").toString()));
    }
    private static void assertNonValueTerminatorThrows(Consumer<VStream<String>> handler) {
        assertThrows(WrappedException.class, () -> handler.accept(streamThatHasThrown()));
    }
    private static void assertValueTerminatorShouldHoldThrown(Function<VStream<String>, Value<String>> handler) {
        assertValueHoldsCheckedException(handler.apply(streamThatHasThrown()));
    }
    private static void assertOpShouldHoldThrown(Function<VStream<String>, VStream<String>> opThatThrows) {
        assertValueHoldsCheckedException(opThatThrows.apply(VStream.of("a","b","c")).findFirst());
    }
    private static void assertOpThrows(Consumer<VStream<String>> op) {
        assertThrows(WrappedException.class, () -> op.accept(streamThatHasThrown()));
    }

    @Test void ofStreams() {
        assertThat(VStream.of((Stream<String>[])null).toList().isEmpty(), is(true));
        assertThat(VStream.of(new Stream<?>[0]).toList().isEmpty(), is(true));
        assertThat(VStream.of(null, (Stream<String>)null).toList().isEmpty(), is(true));
        assertThat(VStream.of(null, null, (Stream<String>)null).toList().isEmpty(), is(true));
        assertThat(VStream.of(Stream.of("a","b")).toList(), is(List.of("a","b")));
        assertThat(VStream.of(Stream.of("a","b"), Stream.of("c","d")).toList(), is(List.of("a","b","c","d")));
        assertThat(VStream.of(Stream.of("a","b"), Stream.of("c","d"), Stream.of("e","f")).toList(), is(List.of("a","b","c","d","e","f")));
    }
    @Test void ofVStreams() {
        assertThat(VStream.of((VStream<String>[])null).toList().isEmpty(), is(true));
        assertThat(VStream.of(new VStream<?>[0]).toList().isEmpty(), is(true));
        assertThat(VStream.of(null, (VStream<String>)null).toList().isEmpty(), is(true));
        assertThat(VStream.of(null, null, (VStream<String>)null).toList().isEmpty(), is(true));
        assertThat(VStream.of(VStream.of("a","b")).toList(), is(List.of("a","b")));
        assertThat(VStream.of(VStream.of("a","b"), VStream.of("c","d")).toList(), is(List.of("a","b","c","d")));
        assertThat(VStream.of(VStream.of("a","b"), VStream.of("c","d"), VStream.of("e","f")).toList(), is(List.of("a","b","c","d","e","f")));
    }
    @Test void ofNullables() {
        assertThat(VStream.of((Object[])null).toList().isEmpty(), is(true));
        assertThat(VStream.of((Object)null).toList().size(), is(1));
        assertThat(VStream.of(null, (Object)null).toList().size(), is(2));
        assertThat(VStream.of("a", "b", "c").toList(), is(List.of("a", "b", "c")));
    }
    @Test void ofCollected() {
        final List<String> twoNulls = new ArrayList<>() {{ add(null); add(null); }};
        assertThat(VStream.ofCollected().toList().isEmpty(), is(true));
        assertThat(VStream.ofCollected(null, null).toList(), is(twoNulls));
        assertThat(VStream.ofCollected(VStream.of("a","b","c")).toList(), is(List.of("a","b","c")));
        assertThat(VStream.ofCollected(Stream.of("a","b","c")).toList(), is(List.of("a","b","c")));
        assertThat(VStream.ofCollected(List.of(1,2,3)).toList(), is(List.of(1,2,3)));
        assertThat(VStream.ofCollected(Map.of("a",1)).toList(), is(List.of(Tuple.of("a",1))));
        assertThat(VStream.ofCollected(new int[] {1, 2, 3 }).toList(), is(List.of(1,2,3)));
        assertThat(VStream.ofCollected("a").toList(), is(List.of("a")));
        assertThat(VStream.ofCollected(
          VStream.of("a1","b1","c1"),
          Stream.of("a2","b2","c2"),
          List.of("a3","b3","c3"),
          Map.of("a4",4),
          new int[] { 1, 2, 3 },
          IntStream.of(4, 5, 6),
          "abc",
          123
        ).toList(), is(List.of("a1","b1","c1","a2","b2","c2","a3","b3","c3",Tuple.of("a4",4), 1,2,3, 4,5,6, "abc", 123)));
    }
    @Test void ofCollectedMapped() {
        assertThat(VStream.ofCollectedMapped(Object::toString, 1).toList(), is(List.of("1")));
        assertThat(VStream.ofCollectedMapped(Object::toString, List.of(1, 2), "a", "b").toList(), is(List.of("1","2","a","b")));
        assertThat(VStream.ofCollectedMapped(Object::toString, 1, null, 2).toList(), is(VStream.of("1",null,"2").toList()));
    }

    @Test void empty() {
        assertThat(VStream.empty().toList().isEmpty(), is(true));
    }
    @Test void toStream() {
        assertThat(VStream.of("a", "b", "c").toStream().collect(Collectors.toList()), is(List.of("a", "b", "c")));
        assertThrows(EX_UNCHECKED_TYPE, () -> VStream.of("a", "b").filter(e -> e.length() > 0).toStream().filter(e -> { throwUnchecked(); return true; }).findFirst());
    }

    @Test void concatThrowingStream() {
        assertThat(VStream.of("a","b").concat(VStream.of("c","d")).toList(), is(List.of("a","b","c","d")));
    }
    @Test void concatStream() {
        assertThat(VStream.of("a","b").concat(Stream.of("c","d")).toList(), is(List.of("a","b","c","d")));
    }

    @Test void combine() {
        assertThat(VStream.of("a","b"    ).combine(VStream.of(1,2  ), (a, b) -> a + b).toList(), is(List.of("a1","b2")));
        assertThat(VStream.of("a","b","c").combine(VStream.of(1,2  ), (a, b) -> a + b).toList(), is(List.of("a1","b2")));
        assertThat(VStream.of("a","b"    ).combine(VStream.of(1,2,3), (a, b) -> a + b).toList(), is(List.of("a1","b2")));
        assertThat(VStream.of("a","b")
            .combine(VStream.of(1,2), VStream.of(true, false),
                (a, b, c) -> a + b + c).toList(),
            is(List.of("a1true","b2false")));
    }

    @Test void iterator() {
        final Iterator<String> it = VStream.of("a", "b", "c").iterator();
        assertThat(it.next(), is("a"));
        assertThat(it.next(), is("b"));
        assertThat(it.next(), is("c"));
        assertThat(it.hasNext(), is(false));
    }
    @Test void spliterator() {
        final Spliterator<String> sp = VStream.of("a", "b", "c").spliterator();
        assertThat(sp.tryAdvance(s -> assertThat(s, is("a"))), is(true));
        assertThat(sp.tryAdvance(s -> assertThat(s, is("b"))), is(true));
        assertThat(sp.tryAdvance(s -> assertThat(s, is("c"))), is(true));
        assertThat(sp.tryAdvance(s -> notSupposedToGetHere()), is(false));
    }
    @Test void parallel() {
        final VStream<String> ts = VStream.of("a", "b", "c");
        assertThat(ts.isParallel(), is(false));
        assertThat(ts.parallel().isParallel(), is(true));
        assertThat(ts.parallel().sequential().isParallel(), is(false));
    }
    @Test void sequential() {
        final VStream<String> ts = VStream.of("a", "b", "c");
        assertThat(ts.sequential().isParallel(), is(false));
        assertThat(ts.parallel().sequential().isParallel(), is(false));
    }
    @Test void unordered() {
        assertThat(VStream.of("a", "b", "c").unordered().toSet(), is(Set.of("a", "b", "c")));
    }
    @Test void onClose() {
        final int[]           called = { 0 };
        final VStream<String> ts     = VStream.of("a", "b", "c").onClose(() -> called[0]++);
        ts.count();
        assertThat(called[0], is(0));
        ts.close();
        assertThat(called[0], is(1));

        final VStream<String> ts2 = VStream.of("a", "b", "c").onClose(VStreamTest::throwChecked);
        assertDoesNotThrow(ts2::close);
    }

    @Test void mapThrown() {
        assertThat(VStream.of("a", "b", "c", "zz")
                          .filter(s -> s.length() == 1)
                          .filter(s -> { throwChecked(); return true; })
                          .mapThrown(t -> VStream.of("k", "l", "m", "other"))
                          .filter(s -> s.length() == 1)
                          .toList(), is(List.of("k", "l", "m")));
    }

    @Test void toList() {
        assertThat(VStream.of("a", "b", "c").toList(), is(List.of("a", "b", "c")));
        assertNonValueTerminatorThrows(VStream::toList);
    }
    @Test void toSet() {
        assertThat(VStream.of("a", "b", "c").toSet(), is(Set.of("a", "b", "c")));
        assertNonValueTerminatorThrows(VStream::toSet);
    }
    @Test void toMap() {
        assertThat(VStream.of("a1", "b2", "c3", "b4").toMap(e -> e.substring(0,1), e -> e.substring(1)), is(Map.of("a","1", "b","4", "c","3")));
        assertNonValueTerminatorThrows(s -> s.toMap(a -> a, b -> b));
    }
    @Test void toMapIgnoreDuplicates() {
        assertThat(VStream.of("a1", "b2", "c3", "b4").toMapIgnoreDuplicates(e -> e.substring(0,1), e -> e.substring(1)), is(Map.of("a","1", "b","2", "c","3")));
        assertNonValueTerminatorThrows(s -> s.toMapIgnoreDuplicates(a -> a, b -> b));
    }
    @Test void toOrderedMap() {
        final Map<String,Integer> map = VStream.of("a1", "b2", "b3", "c4").toOrderedMap(e -> e.substring(0,1), e -> Integer.parseInt(e.substring(1)));
        assertThat(map instanceof LinkedHashMap, is(true));
        assertThat(map, is(Map.of("a",1, "b",3, "c",4)));
        assertNonValueTerminatorThrows(s -> s.toOrderedMap(a -> a, b -> b));
    }
    @Test void toMapAuto() {
        assertThat(VStream.of(Tuple.of("a",1), Tuple.of("b",2), Tuple.of("c",3)).toMap(), is(Map.of("a",1, "b",2, "c",3)));
        assertThat(VStream.of(new SimpleEntry<>("a", 1), new SimpleEntry<>("b", 2), new SimpleEntry<>("c", 3)).toMap(), is(Map.of("a",1, "b",2, "c",3)));
        assertThat(VStream.of(new String[] {"a","1"}, new String[] {"b","2"}, new String[] {"c","3"}).toMap(), is(Map.of("a","1", "b","2", "c","3")));
        assertThat(VStream.of(List.of("a","1"), List.of("b","2"), List.of("c","3")).toMap(), is(Map.of("a","1", "b","2", "c","3")));
        assertThat(VStream.of("a:1","b:2","c:3").map(s->s.split(":")).toMap(), is(Map.of("a","1", "b","2", "c","3")));
        assertThrows(IllegalArgumentException.class, () -> VStream.of("a", "b", "c").toMap());
        assertThrows(IllegalArgumentException.class, () -> VStream.of(new String[] {"a","1"}, new String[] {"b"}).toMap());
        assertThrows(IllegalArgumentException.class, () -> VStream.of(new String[] {"a","1"}, new String[] {"b"}).toMap());
        assertThrows(IllegalArgumentException.class, () -> VStream.of(new String[] {"a","1"}, new String[] {}).toMap());
        assertThrows(IllegalArgumentException.class, () -> VStream.of(new String[] {"a","1"}, null).toMap());
    }

    @Test void nullable() {
        assertThat(VStream.of("a", null, null).skip(1).findFirst().isAbsent(), is(true));
        assertThat(VStream.of("a", null, null).skip(1).findFirst().isNullable(), is(false));
        assertThat(VStream.of("a", null, null).skip(1).nullable().findFirst().isAbsent(), is(false));
        assertThat(VStream.of("a", null, null).skip(1).nullable().findFirst().orElse(""), is(nullValue()));
        assertThat(VStream.of("a", null, null).skip(1).nullable().findFirst().isNullable(), is(true));
        assertThat(VStream.of("a", null, null).skip(1).nullable().notNullable().findFirst().isNullable(), is(false));
    }
    @Test void dropNulls() {
        assertThat(VStream.of("a", null, "b", null, null, "c", null).dropNulls().toList(), is(List.of("a","b","c")));
    }
    @Test void keepWhen() {
        assertThat(VStream.of("a", "bb", "c").keepWhen(s -> s.length() == 1).toList(), is(List.of("a","c")));
        assertOpShouldHoldThrown(stream -> stream.keepWhen(s -> { throwChecked(); return true; }));
    }
    @Test void dropWhen() {
        assertThat(VStream.of("a", "bb", "c").dropWhen(s -> s.length() == 1).toList(), is(List.of("bb")));
        assertOpShouldHoldThrown(stream -> stream.dropWhen(s -> { throwChecked(); return true; }));
    }
    @Test void filter() {
        assertThat(VStream.of("a", "bb", "ccc", "d").filter(s -> s.length() == 1).toList(), is(List.of("a", "d")));
        assertOpShouldHoldThrown(str -> str.filter(s -> { throwChecked(); return true; }));
    }
    @Test void map() {
        assertThat(VStream.of("a", "b", "c").map(s -> "@" + s).toList(), is(List.of("@a", "@b", "@c")));
        assertOpShouldHoldThrown(str -> str.map(s -> { throwChecked(); return ""; }));
    }
    @Test void mapObjectFields() {
        assertThat(VStream.of("a", "b", "c")
            .map(s -> new Object() { String prefix = "@"; String name = s; })
            .map(obj -> obj.prefix + obj.name)
            .toList(), is(List.of("@a", "@b", "@c")));
    }
    @Test void mapTuple2() {
        assertThat(VStream.of("a", "b", "c")
            .map(s -> Tuple.of("@", s))
            .map(String.class, String.class, (prefix, name) -> prefix + name)
            .toList(), is(List.of("@a", "@b", "@c")));
    }
    @Test void mapTuple3() {
        assertThat(VStream.of("a", "b", "c")
            .map(s -> Tuple.of(3, "@", s))
            .map(Integer.class, String.class, String.class, (num, prefix, name) -> num + prefix + name)
            .toList(), is(List.of("3@a", "3@b", "3@c")));
    }
    @Test void mapTuple4() {
        assertThat(VStream.of("a", "b", "c")
            .map(s -> Tuple.of(4, true, "@", s))
            .map(Integer.class, Boolean.class, String.class, String.class, (num, bool, prefix, name) -> "" + num + bool + prefix + name)
            .toList(), is(List.of("4true@a", "4true@b", "4true@c")));
    }
    @Test void flatMap() {
        assertThat(VStream.of("a", "b", "c").flatMap(s -> Stream.of("1@" + s, "2@" + s)).toList(), is(List.of("1@a", "2@a", "1@b", "2@b", "1@c", "2@c")));
        assertOpShouldHoldThrown(str -> str.flatMap(s -> { throwChecked(); return Stream.of("a", "b"); }));
    }
    @Test void distinct() {
        assertThat(VStream.of("a", "a", "b", "c", "c").distinct().toList(), is(List.of("a", "b", "c")));
        assertOpShouldHoldThrown(str -> throwInStream(str).distinct());
    }
    @Test void sorted() {
        assertThat(VStream.of("d", "b", "a", "c", "e").sorted().toList(), is(List.of("a", "b", "c", "d", "e")));
        assertOpShouldHoldThrown(str -> throwInStream(str).sorted());
    }
    @Test void sortedWitComparator() {
        assertThat(VStream.of("D", "B", "a", "c", "e").sorted().toList(), is(List.of("B", "D", "a", "c", "e")));
        assertThat(VStream.of("D", "B", "a", "c", "e").sorted((a, b) -> a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT))).toList(), is(List.of("a", "B", "c", "D", "e")));
        assertOpShouldHoldThrown(str -> str.sorted((a, b) -> { throwChecked(); return 0; }));
    }
    @Test void peek() {
        final int[] callCount = { 0 };
        assertThat(VStream.of("a", "b", "c").peek(s -> callCount[0]++).toList().size(), is(3)); // toList is needed to run the stream
        assertThat(callCount[0], is(3));
        assertOpShouldHoldThrown(str -> str.peek(s -> { throwChecked(); }));
    }
    @Test void limit() {
        assertThat(VStream.of("a", "b", "c", "d", "e").limit(3).toList(), is(List.of("a", "b", "c")));
        assertOpShouldHoldThrown(str -> throwInStream(str).limit(2));
    }
    @Test void skip() {
        assertThat(VStream.of("a", "b", "c", "d", "e").skip(3).toList(), is(List.of("d", "e")));
        assertOpShouldHoldThrown(str -> throwInStream(str).skip(2));
    }
    @Test void takeWhile() {
        assertThat(VStream.of("a", "b", "c", "d").takeWhile(s -> !"c".equals(s)).toList(), is(List.of("a", "b")));
        assertOpShouldHoldThrown(str -> str.takeWhile(s -> { throwChecked(); return true; }));
    }
    @Test void dropWhile() {
        assertThat(VStream.of("a", "b", "c", "d").dropWhile(s -> !"c".equals(s)).toList(), is(List.of("c", "d")));
        assertOpShouldHoldThrown(str -> str.dropWhile(s -> { throwChecked(); return true; }));
    }
    @Test void forEach() {
        final int[] callCount = { 0 };
        VStream.of("a", "b", "c").forEach(s -> callCount[0]++);
        assertThat(callCount[0], is(3));
        assertThrows(WrappedException.class, () -> VStream.of("a", "b").forEach(s -> { throwChecked(); }));
        assertThrows(WrappedException.class, () -> streamThatHasThrown().forEach(s -> {}));
    }
    @Test void forEachOrdered() {
        final int[] callCount = { 0 };
        VStream.of("a", "b", "c").forEachOrdered(s -> callCount[0]++);
        assertThat(callCount[0], is(3));
        assertThrows(WrappedException.class, () -> VStream.of("a", "b").forEachOrdered(s -> { throwChecked(); }));
        assertThrows(WrappedException.class, () -> streamThatHasThrown().forEachOrdered(s -> {}));
    }
    @Test void toArray() {
        assertThat(VStream.of("a", "b", "c").toArray(), is(new Object[] {"a", "b", "c"}));
        assertNonValueTerminatorThrows(VStream::toArray);
    }
    @Test void toArrayWithGenerator() {
        assertThat(VStream.of("a", "b", "c").toArray(String[]::new), is(new String[] {"a", "b", "c"}));
        assertNonValueTerminatorThrows(stream -> stream.toArray(String[]::new));
    }
    @Test void reduce() {
        assertThat(VStream.of("a", "b", "c", "d").reduce((all, s) -> all + s).orElse(""), is("abcd"));
        assertThat(VStream.of("a", "b", "c", "d").nullable().reduce((all, s) -> null).orElse(""), is(nullValue()));
        assertValueTerminatorShouldHoldThrown(str -> str.reduce((all, s) -> ""));
        assertValueHoldsCheckedException(VStream.of("a", "b").reduce((all, s) -> { throwChecked(); return ""; }));
    }
    @Test void reduceWithIdentity() {
        assertThat(VStream.of("a", "b", "c", "d").reduce("!!", (all, s) -> all + s), is("!!abcd"));
        assertNonValueTerminatorThrows(stream -> stream.reduce("!!", (a, b) -> ""));
        assertThrows(WrappedException.class, () -> VStream.of("a", "b").reduce("!!", (all, s) -> { throwChecked(); return ""; }));
    }
    @Test void reduceWithIdentityAndCombiner() {
        assertThat(VStream.of("a", "b", "c", "d").parallel().reduce("!!", (all, s) -> all + s, (a, b) -> a + b), is("!!a!!b!!c!!d"));
        assertNonValueTerminatorThrows(stream -> stream.reduce("!!", (a, b) -> "", (a, b) -> a + b));
        assertThrows(WrappedException.class, () -> VStream.of("a", "b").reduce("!!", (all, s) -> { throwChecked(); return ""; }, (a, b) -> a + b));
    }
    @SuppressWarnings("Convert2MethodRef")
    @Test void collectFromLambdas() {
        assertThat(VStream.of("a", "b", "c", "d").parallel().collect(
          () -> new ArrayList<String>(),
          ArrayList::add,
          (listA, listB) -> listA.addAll(listB)
        ), is(List.of("a","b","c", "d")));
        assertThrows(WrappedException.class, () -> VStream.of("a", "b", "c").collect(() -> new ArrayList<String>(), (a, b) -> throwChecked(), (listA, listB) -> List.of(listA, listB)));
        assertNonValueTerminatorThrows(stream -> stream.collect(() -> new ArrayList<String>(), ArrayList::add, (listA, listB) -> List.of(listA, listB)));
    }
    @Test void collect() {
        assertThat(VStream.of("a", "b", "c").filter(s -> true).collect(Collectors.toList()), is(List.of("a","b","c")));
        assertThrows(WrappedException.class, () -> VStream.of("a", "b", "c").filter(s -> { throwChecked(); return true; }).collect(Collectors.toList()));
        assertNonValueTerminatorThrows(stream -> stream.collect(Collectors.toList()));
    }
    @Test void min() {
        assertThat(VStream.of("d", "b", "a", "c").min(String::compareTo).orElse(""), is("a"));
        assertValueTerminatorShouldHoldThrown(str -> str.min(String::compareTo));
    }
    @Test void max() {
        assertThat(VStream.of("d", "b", "a", "c").max(String::compareTo).orElse(""), is("d"));
        assertValueTerminatorShouldHoldThrown(str -> str.max(String::compareTo));
    }

    @Test void count() {
        assertThat(VStream.of("a", "b", "c").count(), is(3L));
        assertNonValueTerminatorThrows(VStream::count);
    }
    @Test void anyMatch() {
        assertThat(VStream.of("a", "b", "cc").anyMatch(s -> s.length() > 1), is(true));
        assertNonValueTerminatorThrows(ts -> ts.anyMatch(s -> s.length() > 1));
    }
    @Test void allMatch() {
        assertThat(VStream.of("a", "b", "cc").allMatch(s -> s.length() > 1), is(false));
        assertNonValueTerminatorThrows(ts -> ts.allMatch(s -> s.length() > 1));
    }
    @Test void noneMatch() {
        assertThat(VStream.of("a", "b", "cc").noneMatch(s -> s.length() > 2), is(true));
        assertNonValueTerminatorThrows(ts -> ts.noneMatch(s -> s.length() > 2));
    }

    @Test void findFirst() {
        assertThat(VStream.of("a", "b", "c").findFirst(), is(Value.of("a")));
        assertThat(VStream.of("a", "b", "c").filter(e -> e.length() > 1).findFirst(), is(Value.empty()));
        assertValueHoldsCheckedException(streamThatHasThrown().findFirst());
    }
    @Test void findAny() {
        assertThat(VStream.of("a").findAny(), is(Value.of("a")));
        assertThat(VStream.of("a", "b", "c").filter(e -> e.length() > 1).findAny(), is(Value.empty()));
        assertValueHoldsCheckedException(streamThatHasThrown().findAny());
    }

    @Test void iterate() {
        assertThat(VStream.iterate(1, n -> n + 2).limit(5).toList(), is(List.of(1,3,5,7,9)));
        assertValueHoldsCheckedException(VStream.iterate(1, n -> { if(((Integer)7).equals(n + 2)) throwChecked(); return n + 2; }).skip(10).findFirst());
    }
    @Test void iterateHasNext() {
        assertThat(VStream.iterate(1, n -> n < 10, n -> n + 2).toList(), is(List.of(1,3,5,7,9)));
        assertValueHoldsCheckedException(VStream.iterate(1, n -> n < 10, n -> { if(((Integer)7).equals(n + 2)) throwChecked(); return n + 2; }).skip(10).findFirst());
    }
    @Test void generate() {
        final int[] source = { 1, 2, 3, 5, 8, 13 };
        final int[] pos = { 0 };
        assertThat(VStream.generate(() -> source[pos[0]++]).limit(6).toList(), is(VStream.ofCollected(source).toList()));
        pos[0] = 0;
        assertValueHoldsCheckedException(VStream.generate(() -> { final int n = source[pos[0]++]; if(n == 5) throwChecked(); return n; }).skip(10).findFirst());
    }

    @Test void thrownShouldSkipClosures() {
        streamThatHasThrown() // once a stream is in the 'thrown' state, all operations should be skipped
          .keepWhen(s -> { notSupposedToGetHere(); return true; })
          .dropWhen(s -> { notSupposedToGetHere(); return true; })
          .filter(s -> { notSupposedToGetHere(); return true; })
          .map(s -> { notSupposedToGetHere(); return ""; })
          .flatMap(s -> { notSupposedToGetHere(); return null; })
          .sorted((a,b) -> { notSupposedToGetHere(); return 0; })
          .takeWhile(s -> { notSupposedToGetHere(); return true; })
          .dropWhile(s -> { notSupposedToGetHere(); return true; })
          .findFirst(); // terminator runs the stream

        assertOpThrows(ts -> ts.forEach(         s -> { notSupposedToGetHere(); }));
        assertOpThrows(ts -> ts.forEachOrdered(  s -> { notSupposedToGetHere(); }));
        assertOpThrows(ts -> ts.reduce("!!", (a,b) -> { notSupposedToGetHere(); return ""; }));
        assertOpThrows(ts -> ts.reduce("!!", (a,b) -> { notSupposedToGetHere(); return ""; },   (a,b) -> { notSupposedToGetHere(); return ""; }));
        assertOpThrows(ts -> ts.collect(        () -> null, (a,b) -> { notSupposedToGetHere(); }, (a,b) -> { notSupposedToGetHere(); }));
    }
}
