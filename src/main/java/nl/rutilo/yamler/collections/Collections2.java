package nl.rutilo.yamler.collections;

import nl.rutilo.yamler.utils.Tuple.Tuple2;
import nl.rutilo.yamler.utils.Value;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class Collections2 {
    private Collections2() {}

    @SafeVarargs
    public static <T> List<T> concat(List<T>... lists) {
        if(lists == null) return Collections.emptyList();
        if(lists.length == 1) return lists[0] == null ? Collections.emptyList() : lists[0];
        if(lists.length == 2 && lists[0] == null) return lists[1] == null ? Collections.emptyList() : lists[1];
        if(lists.length == 2 && lists[1] == null) return lists[0];

        return Stream.of(lists)
            .filter(Objects::nonNull)
            .reduce(new ArrayList<>(), (all, list) -> { all.addAll(list); return all; });
    }

    public static <T> List<List<T>> split(List<T> listIn, int tupleSize) {
        if(listIn == null || listIn.size() < tupleSize || tupleSize == 0) {
            return Collections.emptyList();
        }
        final int newLen = listIn.size() / tupleSize;
        final List<List<T>> splitList = new ArrayList<>();
        for(int i=0; i<newLen; i++) {
            splitList.add(listIn.subList(i * tupleSize, (i+1) * tupleSize));
        }
        return splitList;
    }

    public static <R,A,B> List<R> map(Collection<A> a, Collection<B> b, BiFunction<A,B,R> mapper) {
        final Iterator<A> aIterator = a.iterator();
        final Iterator<B> bIterator = b.iterator();
        final List<R> mapped = new ArrayList<>();
        while(aIterator.hasNext() && bIterator.hasNext()) {
            mapped.add(mapper.apply(aIterator.next(), bIterator.next()));
        }
        while(aIterator.hasNext()) {
            mapped.add(mapper.apply(aIterator.next(), null));
        }
        while(bIterator.hasNext()) {
            mapped.add(mapper.apply(null, bIterator.next()));
        }
        return mapped;
    }

    public static <T> Value<T> firstOf(Collection<T> collection) {
        if(collection.isEmpty()) return Value.absent();
        if(collection instanceof List) return Value.of(((List<T>)collection).get(0));
        return Value.ofOptional(collection.stream().findFirst());
    }

    public static <T> Value<T> lastOf(Collection<T> collection) {
        if(collection.isEmpty()) return Value.absent();
        if(collection instanceof List) return Value.of(((List<T>)collection).get(collection.size()-1));
        return Value.ofOptional(collection.stream().reduce((a, b) -> b));
    }

    @SuppressWarnings("unchecked") // obj is converted using instanceof, so it is safe to do a cast
    public static <T> Collection<T> toCollection(final Object obj) {
        if(obj == null) return Collections.emptyList();
        if(obj instanceof Collection) return (Collection<T>)obj;
        return Collections.singletonList((T)obj);
    }

    public static Map<?,?> toMap(Object obj) { // NOSONAR wildcard because any type
        if(obj == null) return Collections.emptyMap();
        if(obj instanceof Map) return (Map<?,?>)obj;
        if(obj instanceof List<?> list) {
            if(list.isEmpty()) return Collections.emptyMap();
            return Map.ofEntries(list.stream()
              .map(Collections2::toMapEntry)
              .toArray(Map.Entry[]::new)
            );
        }
        if(obj.getClass().isArray()) {
            if(Array.getLength(obj) == 0) return Collections.emptyMap();
            return Map.ofEntries(Stream.of((Object[])obj)
              .map(Collections2::toMapEntry)
              .toArray(Map.Entry[]::new)
            );
        }
        return Map.ofEntries(toMapEntry(obj));
    }

    public static <A,B> Map.Entry<A,B> toMapEntry(A a, B b) {
        return new AbstractMap.SimpleEntry<>(a, b);
    }

    public static Map.Entry<?,?> toMapEntry(Object obj) { // NOSONAR wildcard because any type
        if(obj == null) return toMapEntry(null,null);
        if(obj instanceof Map.Entry) {
            return (Map.Entry<?,?>)obj;
        }
        if(obj instanceof Tuple2) {
            return toMapEntry(
              ((Tuple2<?,?>)obj).a,
              ((Tuple2<?,?>)obj).b
            );
        }
        if(obj.getClass().isArray()) {
            return toMapEntry(
              Array.getLength(obj) > 0 ? Array.get(obj, 0) : null,
              Array.getLength(obj) > 1 ? Array.get(obj, 1) : null
            );
        }
        if(obj instanceof final List<?> list) {
            return toMapEntry(
              !list.isEmpty() ? list.get(0) : null,
              list.size() > 1 ? list.get(1) : null
            );
        }
        return toMapEntry(obj, obj);
    }
}
