package nl.rutilo.yamler.collections;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import nl.rutilo.yamler.testutils.IsMatcher;
import nl.rutilo.yamler.utils.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class StringKeyMapTest {
    @SuppressWarnings("ClassCanBeRecord")
    @ToString
    @EqualsAndHashCode
    @RequiredArgsConstructor
    public static class TestClassWithConstructor {
        public final int a;
        public final String s;
        public final double d;
    }

    @Test void testEmpty() {
        assertThat(StringKeyMap.empty().size(), is(0));
        assertThrows(IllegalStateException.class, () -> StringKeyMap.empty().put("a", "b")); // NOSONAR
    }
    @Test void asImmutableShouldThrowOnMutation() {
        assertThrows(IllegalStateException.class, // NOSONAR
            () -> StringKeyMap.from(Map.of("a",1, "b", 2)).asImmutable().put("c", 3));
    }
    @Test void testGetAndPutOfVariousTypes() {
        final StringKeyMap mapCopy = StringKeyMap.from(Map.of(1, "a", 2, "b"));
        assertThat(mapCopy.get("1"), is("a"));
        assertThat(mapCopy.get("2"), is("b"));

        final StringKeyMap map = new StringKeyMap();
        map.put("int", 123);
        map.put("long", 987L);
        map.put("short", (short)123);
        map.put("double", 333.5d);
        map.put("float", 444.5f);
        map.put("byte", (byte)12);
        map.put("bool", true);
        map.put("string", "text");
        map.put("intArray", List.of(1, 2, 3));
        map.put("map", Map.of("a",1, "b",2, "c",3));
        map.put("class", new TestClassWithConstructor(123,"text", 456L));
        map.put("null", null);

        assertThat(map.get("int", 0), is(123));
        assertThat(map.get("long", 0L), is(987L));
        assertThat(map.get("short", (short)0), is((short)123));
        assertThat(map.get("double", 0d), is(333.5d));
        assertThat(map.get("float", 0f), is(444.5f));
        assertThat(map.get("byte", (byte)0), is((byte)12));
        assertThat(map.get("bool", false), is(true));
        assertThat(map.get("string", ""), is("text"));
        assertThat(map.get("intArray", Set.of(123)), is(Set.of(1,2,3)));
        assertThat(map.get("intArray", List.of(123)), is(List.of(1,2,3)));
        assertThat(map.get("map", Map.of("a",11)), is(Map.of("a",1, "b",2, "c",3)));
        assertThat(map.get("class.a", 0), is(123));
        assertThat(map.get("class.s", ""), is("text"));
        assertThat(map.get("class.d", 456), is(456));
        assertThat(map.get("null"), is(nullValue()));
        assertThat(map.containsKey("null"), is(true));

        assertThat(map.getValue("null"), IsMatcher.is(Value.ofNullable(null)));
        assertThat(map.getValue("int").orElse(0), is(123));
        assertThat(map.getValue("nonexistent"), is(Value.empty()));

        assertThat(map.getIgnoreCase("INT"), is(123));
        map.put("int2", 456);
        assertThat(map.getIgnoreCase("INT2"), is(456));
    }
    @Test void testGetWithPath() {
        final StringKeyMap map = new StringKeyMap();

        final List<Object> list = new ArrayList<>();
        list.add("first");
        list.add("second");
        list.add("third");
        list.add(new StringKeyMap(Map.of("m1",1,"m2",2,"list", List.of("a","b",List.of("CA","CB","CC",List.of("CDA",Map.of("CDB1",1),567))))));

        final StringKeyMap map2 = new StringKeyMap();
        map2.put("a", 1);
        map2.put("list", list);
        map2.put("foo.bar", "123");
        map2.put("key.[4]", "456");
        map.put("map", map2);
        assertThat(map.get("map"), IsMatcher.is(map2));
        assertThat(map.get("map.a"), is(1));
        assertThat(map.get("map.key\\.\\[4\\]"), is("456"));
        assertThat(map.get("map.list.0"), is("first"));
        assertThat(map.get("map.list.3.m1"), is(1));
        assertThat(map.get("map.list.3.m2"), is(2));
        assertThat(map.get("map.list.3.list[1]"), is("b"));
        assertThat(map.get("map.list.3.list[2].0"), is("CA"));
        assertThat(map.get("map.list.3.list[2][1]"), is("CB"));
        assertThat(map.get("map.list.3.list[2].2"), is("CC"));
        assertThat(map.get("map.list.3.list[2][3][0]"), is("CDA"));
        assertThat(map.get("map.list.3.list[2].3.[1].CDB1"), is(1));
        assertThat(map.get("map.list.3.list[2].3[2"), is(567));
    }
    @Test void testGetCollectionWithEmptyDefault() {
        final StringKeyMap map = new StringKeyMap();
        map.put("strings", List.of("abc", "def"));
        map.put("ints", List.of(1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> map.get("strings", new ArrayList<>())); // NOSONAR
        assertThat(map.get("strings", new ArrayList<>(), String.class), is(List.of("abc", "def")));
        assertThat(map.get("ints", new ArrayList<>(), Integer.TYPE), is(List.of(1, 2, 3)));
        assertThat(map.get("ints", new ArrayList<Long>(), Long.class), is(List.of(1L, 2L, 3L)));
    }
    @Test void testHandle() {
        final StringKeyMap map = new StringKeyMap(
          "int", 123,
          "float", 445.6f,
          "string", "abc",
          "list", List.of("a", "b", "c"),
          "map", Map.of("a",1, "b",2, "c",3),
          "null", null
        );
        map
          .handle("int", 0, i -> assertThat(i, is(123)))
          .handle("float", 0f, f -> assertThat(f, is(445.6f)))
          .handle("string", "", s -> assertThat(s, is("abc")))
          .handle("list", Collections.<String>emptyList(), l -> assertThat(l, is(List.of("a", "b", "c"))))
          .handle("map", Collections.<String,Integer>emptyMap(), m -> assertThat(m, is(Map.of("a",1, "b",2, "c",3))))
          .handle("null", null, n -> assertThat(n, is(nullValue())))
          .handle("nonexisting", null, n -> assertThat(n, is(nullValue())));
          ;
    }
    @Test void testHandleIfExists() {
        final StringKeyMap map = new StringKeyMap(
          "int", "123"
        );
        final boolean[] called = { false };
        map.handleIfExists("int", Integer.class, i -> { called[0] = true; assertThat(i, is(123)); });
        assertThat(called[0], is(true));
        assertThat(map.handleIfExists("nonexistent", Integer.class, n -> { fail(); }), is(map));
    }
    @Test void testJoin() {
        final StringKeyMap map1 = StringKeyMap.from(Map.of(
            "text", "textValue1",
            "number", 123,
            "numberList", List.of(1,2,3),
            "map", Map.of(
                "mapText", "mapTextValue",
                "mapNumber", 234,
                "mapNumberList", List.of(2,3,4),
                "mapMap", Map.of(
                    "mapDeepText", "mapDeepTextValue",
                    "mapDeepNumber", 345,
                    "mapDeepNumberList", List.of(3,4,5)
                )
            ),
            "amap", Map.of("akey",111)
        ));
        final StringKeyMap map2 = StringKeyMap.from(Map.of(
           "number", 112233,
           "map", Map.of(
               "mapNumber", 223344,
               "mapNumberList", List.of(22,33,44),
                "mapMap", Map.of(
                    "mapDeepNumber", 334455
                )
            ),
            "bmap", Map.of("bkey",222)
        ));
        final StringKeyMap joinedExpected = StringKeyMap.from(Map.of(
            "text", "textValue1",
            "number", 112233,
            "numberList", List.of(1,2,3),
            "map", Map.of(
                "mapText", "mapTextValue",
                "mapNumber", 223344,
                "mapNumberList", List.of(22,33,44),
                "mapMap", Map.of(
                    "mapDeepText", "mapDeepTextValue",
                    "mapDeepNumber", 334455,
                    "mapDeepNumberList", List.of(3,4,5)
                )
            ),
            "amap", Map.of("akey",111),
            "bmap", Map.of("bkey",222)
        ));
        final StringKeyMap joined = StringKeyMap.join(map1, map2);
        joined.sortKeys();
        joinedExpected.sortKeys();
        assertThat(joined.toJsonString(2), is(joinedExpected.toJsonString(2)));
        assertThat(joined.toJsonString(), is(joinedExpected.toJsonString()));
    }
    @Test void testMapKeys() {
        final StringKeyMap map = new StringKeyMap()
            .putc("a", 1)
            .putc("b", 2)
            .putc("c", 3);
        map.mapKeys(key -> "@" + key);
        assertThat(map.size(), is(3));
        assertThat(map.get("@a"), is(1));
        assertThat(map.get("@b"), is(2));
        assertThat(map.get("@c"), is(3));
    }
    @Test void testMapValues() {
        final StringKeyMap map = new StringKeyMap()
            .putc("a", 1)
            .putc("b", 2)
            .putc("c", 3);
        map.mapValues(val -> 10 * (int)val);
        assertThat(map.size(), is(3));
        assertThat(map.get("a"), is(10));
        assertThat(map.get("b"), is(20));
        assertThat(map.get("c"), is(30));
    }
    @Test void testJoinWith() {
        final StringKeyMap map = new StringKeyMap()
            .putc("a", "A")
            .putc("b", "B")
            .putc("c", "C");
        final Map<String,String> map2 = Map.of("a", "AA", "d", "DD");
        final StringKeyMap joinedMap = map.joinWith(map2);
        assertThat(map == joinedMap, is(false));
        assertThat(joinedMap.size(), is(4));
        assertThat(joinedMap.get("a"), is("AA"));
        assertThat(joinedMap.get("b"), is("B"));
        assertThat(joinedMap.get("c"), is("C"));
        assertThat(joinedMap.get("d"), is("DD"));
        final StringKeyMap joinedMap2 = joinedMap.joinWith(new StringKeyMap().putc("e", "EEE").putc("f", "FFF"));
        assertThat(joinedMap2.size(), is(6));
        assertThat(joinedMap2.get("a"), is("AA"));
        assertThat(joinedMap2.get("b"), is("B"));
        assertThat(joinedMap2.get("c"), is("C"));
        assertThat(joinedMap2.get("d"), is("DD"));
        assertThat(joinedMap2.get("e"), is("EEE"));
        assertThat(joinedMap2.get("f"), is("FFF"));
    }
}
