package nl.rutilo.yamler.collections;

import nl.rutilo.yamler.objectmapper.RSObjectMapper;
import nl.rutilo.yamler.objectmapper.RSObjectMapperException;
import nl.rutilo.yamler.utils.StringUtils;
import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingConsumer;
import nl.rutilo.yamler.yamler.Yamler;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({
    "squid:S2160" // (override equals()) false positive: this subclass does not add any (non-transient) state
    , "unchecked"})
public class StringKeyMap extends LinkedHashMap<String, Object> {
    private final transient Map<String, String> lcToKey = new HashMap<>();
    private final transient IntChange hashChanged = new IntChange(this::updateKeyCasings);
    private transient boolean useDottedPaths = true;

    private static <T> T toTargetObjectOrDefault(Object value, Class<T> targetType, Type genericType, T defaultValue) {
        try {
            return Value.of(RSObjectMapper.toTargetObject(value, targetType, genericType))
                .orElse(defaultValue);
        } catch (final RSObjectMapperException e) {
            throw e;
        } catch (final Exception e) {
            return defaultValue;
        }
    }

    private static <T extends Number> T  toNumber(Object value, T defaultValue) {
        try {
            //noinspection unchecked (class of T may not be Class<T>?)
            return value instanceof Number ? toTargetObjectOrDefault(value, (Class<T>) defaultValue.getClass(), null, defaultValue) : defaultValue;
        } catch (final Exception e) {
            return defaultValue;
        }
    }

    public static Map<String, Object> keysToString(Map<?, ?> map) {
        return map.entrySet().stream()
          .filter(entry -> null != entry.getValue())
          .collect(Collectors.toMap(e -> Objects.toString(e.getKey()), Map.Entry::getValue));
    }

    public static StringKeyMap from(Map<?, ?> map) {
        return new StringKeyMap(keysToString(map));
    }
    public static StringKeyMap convertFrom(Map<?, ?> map) {
        return map instanceof StringKeyMap stringKeyMap ? stringKeyMap : from(map);
    }
    public static StringKeyMap empty() { return ImmutableStringKeyMap.EMPTY; }

    public StringKeyMap() {}
    public StringKeyMap(Map<String, Object> fromMap) {
        super(fromMap);
    }
    public StringKeyMap(String key, Object val, Object... keyVals) {
        put(key, val);
        for (int i = 0; i < keyVals.length; i += 2) {
            if (keyVals[i] != null) put(keyVals[i].toString(), keyVals[i + 1]);
        }
    }

    public StringKeyMap asImmutable() { return new ImmutableStringKeyMap(this); }

    private void checkKeyCasings() {
        hashChanged.check(hashCode());
    }
    private void updateKeyCasings() {
        lcToKey.clear();
        keySet().forEach(key -> lcToKey.put(StringUtils.lc(key), key));
    }

    /**
     * Chained variant of put(key, value)
     */
    public StringKeyMap putc(String key, Object value) {
        put(key, value);
        return this;
    }

    public StringKeyMap setUseDottedPaths(boolean set) { useDottedPaths = set; return this; }

    public Object getIgnoreCase(String key) {
        return get(actualCasingForKey(key));
    }

    public String actualCasingForKey(String key) {
        checkKeyCasings();
        return Value.of(lcToKey.get(key.toLowerCase(Locale.US))).orElse(key);
    }

    public int        get(String key, int defaultValue) {
        return toNumber(get(key), defaultValue);
    }
    public long       get(String key, long defaultValue) {
        return toNumber(get(key), defaultValue);
    }
    public short      get(String key, short defaultValue) {
        return toNumber(get(key), defaultValue);
    }
    public double     get(String key, double defaultValue) {
        return toNumber(get(key), defaultValue);
    }
    public float      get(String key, float defaultValue) {
        return toNumber(get(key), defaultValue);
    }
    public byte       get(String key, byte defaultValue) {
        return toNumber(get(key), defaultValue);
    }
    public boolean    get(String key, boolean defaultValue) {
        return toTargetObjectOrDefault(get(key), Boolean.class, null, defaultValue);
    }
    public String     get(String key, String defaultValue) {
        return containsKey(key) ? get(key).toString() : defaultValue;
    }
    public <T> Set<T>         get(String key, Set<T> defaultValue) {
        checkEmptyCollection(defaultValue, Set::isEmpty);
        final Type type = defaultValue.iterator().next().getClass();
        return toTargetObjectOrDefault(get(key), Set.class, type, defaultValue);
    }
    public <T> List<T>        get(String key, List<T> defaultValue) {
        checkEmptyCollection(defaultValue, List::isEmpty);
        final Type type = defaultValue.iterator().next().getClass();
        return toTargetObjectOrDefault(get(key), List.class, type, defaultValue);
    }
    public <T> Map<String, T> get(String key, Map<String, T> defaultValue) {
        checkEmptyCollection(defaultValue, Map::isEmpty);
        final Type type = defaultValue.values().iterator().next().getClass();
        return toTargetObjectOrDefault(get(key), Map.class, type, defaultValue);
    }
    public <T> T              get(String key, T defaultValue, Class<?> genericType) {
        return toTargetObjectOrDefault(get(key), (Class<T>)defaultValue.getClass(), genericType, defaultValue);
    }
    public <T> T              get(String key, T defaultValue) {
        final Class<T> clazz = defaultValue == null ? (Class<T>) Object.class : (Class<T>) defaultValue.getClass();
        return toTargetObjectOrDefault(get(key), clazz, null, defaultValue);
    }

    public <T> StringKeyMap handle(String key, T defaultValue, ThrowingConsumer<T> handler) {
        final T value = get(key, defaultValue);
        handler.accept(value);
        return this;
    }
    public <T> StringKeyMap handleIfExists(String key, Class<T> type, ThrowingConsumer<T> handler) {
        getValue(key).map(value -> RSObjectMapper.toTargetObject(value, type, null)).ifPresent(handler);
        return this;
    }

    private static <T> void checkEmptyCollection(T collection, Predicate<T> isEmpty) {
        if(collection == null || isEmpty.test(collection)) {
            final String tname = collection == null ? "collection" : collection.getClass().getSimpleName();
            throw new IllegalArgumentException(
                "Due to the limited java typing system the generic type of the " + tname
                    + " cannot be determined when the " + tname + " is empty. "
                    + "Please use get(key, defaultValue, genericType) instead.");
        }
    }

    private static final Pattern DOT_SPLIT_PATTERN = Pattern.compile("(?<!\\\\)([]\\[.]+)");
    private static final Pattern DOT_REPLACE_PATTERN = Pattern.compile("(?<!\\\\)\\\\([]\\[.]+)");

    private Value<Object> getValue(Map<?, ?> srcMap, String key) {
        if(useDottedPaths) {
            final String[] keyParts = DOT_SPLIT_PATTERN.split(key);
            for (int i = 0; i < keyParts.length; i++)
                keyParts[i] = DOT_REPLACE_PATTERN.matcher(keyParts[i]).replaceAll("$1");
            return getValue(srcMap, keyParts, 0);
        } else {
            return getValue(srcMap, new String[] { key }, 0);
        }
    }
    private Value<Object> getValue(Object src, String[] keyParts, int keyIndex) {
        final String key = keyParts[keyIndex];
        Object value = null;
        boolean hasValue = false;
        if (src instanceof List) {
            try {
                value = ((List<?>) src).get(Integer.parseInt(key));
                hasValue = true;
            } catch (final Exception e) { /*value remains null, hasValue false*/ }
        } else if (src instanceof final Map<?, ?> map) {
            value = map.get(key);
            hasValue = value != null || map.containsKey(key);
        } else if (src != null) {
            try {
                value = src.getClass().getField(key).get(src);
                hasValue = true;
            } catch (final Exception e) {
                try {
                    value = src.getClass().getMethod("get" + key.substring(0, 1).toUpperCase() + key.substring(1)).invoke(src);
                    hasValue = true;
                } catch (final Exception e2) { /*value remains null, hasValue false */ }
            }
        }
        return value != null && (keyIndex < keyParts.length - 1)
            ? getValue(value, keyParts, keyIndex + 1)
            : (hasValue ? Value.ofNullable(value) : Value.empty());
    }

    public Object get(String key) {
        return getValue(this, key).orElse(null);
    }
    public Value<Object> getValue(String key) {
        return getValue(this, key);
    }
    public Value<Object> getValueIgnoreCase(String key) {
        return getValue(this, actualCasingForKey(key));
    }

    public Object getOrDefault(String key, Object defaultValue) {
        return getValue(key).orElse(defaultValue);
    }

    public boolean containsKey(String key) {
        return getValue(key).isPresent();
    }

    /** Recursively sorts keys in their natural order. Returns this. */
    public StringKeyMap sortKeys() {
        return sortKeys(Comparator.naturalOrder());
    }
    /** Recursively sorts keys in the given order. Returns this. */
    public StringKeyMap sortKeys(Comparator<String> cmp) {
        final List<String> keys = new ArrayList<>(keySet());
        keys.sort(cmp);
        final Map<String,?> copy = new HashMap<>(this);
        clear();
        keys.forEach(key -> {
            Object value = copy.get(key);
            if(value instanceof Map) { value = StringKeyMap.convertFrom((Map<?,?>)value).sortKeys(); }
            put(key, value);
        });
        return this;
    }

    /** Change the keys by applying a mapper to them. Order stays the same. Mutates this. */
    public void mapKeys(UnaryOperator<String> keyMapper) {
        final List<String> keys = new ArrayList<>(keySet());
        final Map<String,?> copy = new HashMap<>(this);

        clear();
        keys.forEach(key -> put(keyMapper.apply(key), copy.get(key)));
    }
    /** Change the values by applying a mapper to them. Order stays the same. Mutates this. */
    public void mapValues(UnaryOperator<Object> valueMapper) {
        final List<String> keys = new ArrayList<>(keySet());
        final Map<String,?> copy = new HashMap<>(this);

        clear();
        keys.forEach(key -> put(key, valueMapper.apply(copy.get(key))));
    }

    public String toJsonString() { return Yamler.toJsonString(this); }
    public String toJsonString(int indent) { return Yamler.toJsonString(this, indent); }

    public StringKeyMap joinWith(Map<?,?> other) { return join(this, convertFrom(other)); }
    public StringKeyMap joinWith(StringKeyMap other) { return join(this, other); }

    public static StringKeyMap join(Map<?,?> a, Map<?,?> b) {
        return join(convertFrom(a), convertFrom(b));
    }
    public static StringKeyMap join(StringKeyMap a, StringKeyMap b) {
        return Stream.concat(a.entrySet().stream(), b.entrySet().stream())
            .collect(Collectors.toMap(
                /*keyMapper*/   Map.Entry::getKey,
                /*valueMapper*/ Map.Entry::getValue,
                /*merge*/       StringKeyMap::joinValues,
                /*factory*/     StringKeyMap::new)
            );
    }
    public static StringKeyMap join(Collection<StringKeyMap> maps) {
        return maps.stream().reduce(new StringKeyMap(), StringKeyMap::join);
    }
    private static Object joinValues(Object aValue, Object bValue) {
        return (aValue instanceof Map && bValue instanceof Map) ? join((Map<?,?>)aValue, (Map<?,?>)bValue) : bValue;
    }

    private static class IntChange {
        private int n;
        private final Runnable handler;
        public IntChange(Runnable handler) { this(0, handler); }
        public IntChange(int n, Runnable handler) { this.n = n; this.handler = handler; }
        public void check(int newValue) { if(n != newValue) { n = newValue; handler.run(); } }
        public int get() { return n; }
    }
}
