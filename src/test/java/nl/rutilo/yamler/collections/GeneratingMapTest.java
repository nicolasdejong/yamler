package nl.rutilo.yamler.collections;

import nl.rutilo.yamler.testutils.IsMatcher;
import nl.rutilo.yamler.utils.Value;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class GeneratingMapTest {

    @Test void get() {
        final GeneratingMap<String, List<Integer>> map = new GeneratingMap<>(ArrayList::new);
        MatcherAssert.assertThat(map.size(), IsMatcher.is(0));
        map.get("foo").add(1);
        MatcherAssert.assertThat(map.size(), IsMatcher.is(1));
        MatcherAssert.assertThat(map.get("foo").size(), IsMatcher.is(1));
        map.get("foo").add(2);
        MatcherAssert.assertThat(map.size(), IsMatcher.is(1));
        MatcherAssert.assertThat(map.get("foo").size(), IsMatcher.is(2));
    }

    @Test void getValue() {
        final GeneratingMap<String, List<Integer>> map = new GeneratingMap<>(ArrayList::new);
        map.get("foo").add(1);

        MatcherAssert.assertThat(map.getValue("nonExisting"), IsMatcher.is(Value.absent()));
        MatcherAssert.assertThat(map.getValue("foo").orElseThrow(), IsMatcher.is(List.of(1)));
    }
}