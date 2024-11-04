package nl.rutilo.yamler.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

// squid: deprecated only because call will throw -- these methods will not be removed
// deprecated is still used, but only in the test
@SuppressWarnings(
    {"squid:S6355", "squid:S1133", "DeprecatedIsStillUsed"})
public class ImmutableStringKeyMap extends StringKeyMap {
    static final ImmutableStringKeyMap EMPTY = new ImmutableStringKeyMap(new HashMap<>());

    public ImmutableStringKeyMap(StringKeyMap toCopy) {
        super(toCopy);
    }
    public ImmutableStringKeyMap(Map<String,Object> toCopy) {
        super(toCopy);
    }

    private IllegalStateException writeException() { return new IllegalStateException("Trying to change a read only StringKeyMap"); }

    @Override public Set<String> keySet() { return new LinkedHashSet<>(super.keySet()); }
    @Override public Collection<Object> values() { return new ArrayList<>(super.values()); }
    @Override public Set<Map.Entry<String,Object>> entrySet() { return new LinkedHashSet<>(super.entrySet()); }

    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public void clear() { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public Object put(String key, Object value) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public void putAll(Map<? extends String, ?> m) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public Object putIfAbsent(String key, Object value) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public Object remove(Object key) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public boolean replace(String key, Object oldValue, Object newValue) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public Object replace(String key, Object value) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public void replaceAll(BiFunction<? super String, ? super Object, ?> function) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public Object computeIfPresent(String key, BiFunction<? super String, ? super Object, ?> mappingFunction) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public Object compute(String key, BiFunction<? super String, ? super Object, ?> mappingFunction) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public Object merge(String key, Object value, BiFunction<? super Object, ? super Object, ?> mappingFunction) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public StringKeyMap setUseDottedPaths(boolean set) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public StringKeyMap sortKeys() { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public StringKeyMap sortKeys(Comparator<String> cmp) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public void mapKeys(UnaryOperator<String> keyMapper) { throw writeException(); }
    /** @deprecated always throws -- don't try to mutate an immutable */
    @Deprecated @Override public void mapValues(UnaryOperator<Object> valueMapper) { throw writeException(); }
}
