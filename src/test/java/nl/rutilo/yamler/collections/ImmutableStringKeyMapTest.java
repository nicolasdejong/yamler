package nl.rutilo.yamler.collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;

// Calls deprecated methods to test if they throw (only deprecated as warning that they will throw)
@SuppressWarnings("deprecation")
class ImmutableStringKeyMapTest {
    private final ImmutableStringKeyMap map = new ImmutableStringKeyMap(Map.of("a", "AA", "b", "BB"));

    private void assertThrows(Executable toRun) { Assertions.assertThrows(IllegalStateException.class, toRun); }
    private void assertThrows(Supplier<?> toRun) { Assertions.assertThrows(IllegalStateException.class, toRun::get); }

    @Test void keySet()            { assertThat(map.keySet(), is(Set.of("a", "b"))); }
    @Test void values()            { assertThat(new HashSet<>(map.values()), is(Set.of("AA", "BB"))); }
    @Test void entrySet()          { assertThat(map.entrySet(), is(Map.of("a", "AA", "b", "BB").entrySet())); }

    @Test void clear()             { assertThrows(map::clear); }
    @Test void put()               { assertThrows(() -> map.put("c", "CC")); }
    @Test void putAll()            { assertThrows(() -> map.putAll(Map.of("d", "DD", "e", "EE"))); }
    @Test void putIfAbsent()       { assertThrows(() -> map.putIfAbsent("d", "DD")); }
    @Test void remove()            { assertThrows(() -> map.remove("a")); }
    @Test void replaceBy()         { assertThrows(() -> map.replace("a", "AA", "AAA")); }
    @Test void replace()           { assertThrows(() -> map.replace("a", "AA")); }
    @Test void replaceAll()        { assertThrows(() -> map.replaceAll((a,b) -> a+b)); }
    @Test void computeIfAbsent()   { assertThrows(() -> map.computeIfAbsent("a", key -> key + "@")); }
    @Test void computeIfPresent()  { assertThrows(() -> map.computeIfPresent("a", (key, val) -> key + "@")); }
    @Test void compute()           { assertThrows(() -> map.compute("a", (key, val) -> key + "@")); }
    @Test void merge()             { assertThrows(() -> map.merge("a", "b", (val1, val2) -> true)); }
    @Test void setUseDottedPaths() { assertThrows(() -> map.setUseDottedPaths(false)); }
    @Test void sortKeys()          { assertThrows(() -> map.sortKeys()); }
    @Test void sortKeysWithComp()  { assertThrows(() -> map.sortKeys(Comparator.naturalOrder())); }
    @Test void mapKeys()           { assertThrows(() -> map.mapKeys(key -> "@" + key)); }
    @Test void mapValues()         { assertThrows(() -> map.mapValues(val -> "@" + val)); }
}