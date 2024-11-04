package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;


/**
 * Copy of the Comparator function interface with the only difference that
 * this interface allows for checked exceptions to be thrown.
 */
@FunctionalInterface
public interface ThrowingComparator<T> extends Comparator<T> {
    /* Calls {@link Comparator#compare} but allows throwing checked exceptions */
    @Override
    default int compare(T o1, T o2) {
        try {
            return compareThrows(o1, o2);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    int compareThrows(T o1, T o2) throws Exception; // NOSONAR - generic throw

    /* Calls {@link Comparator#reversed} but allows throwing checked exceptions */
    @Override
    default ThrowingComparator<T> reversed() {
        return (ThrowingComparator<T>) Collections.reverseOrder(this);
    }

    /* Calls {@link Comparator#thenComparing} but allows throwing checked exceptions */
    @Override
    default ThrowingComparator<T> thenComparing(Comparator<? super T> other) {
        Objects.requireNonNull(other);
        return (ThrowingComparator<T> & Serializable) (c1, c2) -> {
            int res = compare(c1, c2);
            return (res != 0) ? res : other.compare(c1, c2);
        };
    }

    /* Calls {@link Comparator#thenComparing} but allows throwing checked exceptions */
    default <U> ThrowingComparator<T> thenComparing(
            ThrowingFunction<? super T, ? extends U> keyExtractor,
            ThrowingComparator<? super U> keyComparator)
    {
        return thenComparing(comparing(keyExtractor, keyComparator));
    }

    /* Calls {@link Comparator#thenComparing} but allows throwing checked exceptions */
    default <U extends Comparable<? super U>> ThrowingComparator<T> thenComparing(
            ThrowingFunction<? super T, ? extends U> keyExtractor)
    {
        return thenComparing(comparing(keyExtractor));
    }

    /* Calls {@link Comparator#thenComparingInt} but allows throwing checked exceptions */
    @Override
    default ThrowingComparator<T> thenComparingInt(ToIntFunction<? super T> keyExtractor) {
        return thenComparing(comparingInt(keyExtractor));
    }

    /* Calls {@link Comparator#thenComparingLong} but allows throwing checked exceptions */
    @Override
    default ThrowingComparator<T> thenComparingLong(ToLongFunction<? super T> keyExtractor) {
        return thenComparing(comparingLong(keyExtractor));
    }

    /* Calls {@link Comparator#thenComparingDouble} but allows throwing checked exceptions */
    @Override
    default ThrowingComparator<T> thenComparingDouble(ToDoubleFunction<? super T> keyExtractor) {
        return thenComparing(comparingDouble(keyExtractor));
    }

    /* Calls {@link Comparator#reverseOrder} */
    static <T extends Comparable<? super T>> Comparator<T> reverseOrder() {
        return Collections.reverseOrder();
    }

    /* Calls {@link Comparator#naturalOrder} */
    static <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
        return Comparator.naturalOrder();
    }

    /* Calls {@link Comparator#nullsFirst} but allows throwing checked exceptions */
    static <T> ThrowingComparator<T> nullsFirst(ThrowingComparator<? super T> comparator) {
        //noinspection unchecked
        return (ThrowingComparator<T>)Comparator.nullsFirst(comparator);
    }

    /* Calls {@link Comparator#nullsLast} but allows throwing checked exceptions */
    static <T> ThrowingComparator<T> nullsLast(ThrowingComparator<? super T> comparator) {
        //noinspection unchecked
        return (ThrowingComparator<T>)Comparator.nullsLast(comparator);
    }

    /* Calls {@link Comparator#comparing} but allows throwing checked exceptions */
    static <T, U> ThrowingComparator<T> comparing(
            ThrowingFunction<? super T, ? extends U> keyExtractor,
            ThrowingComparator<? super U> keyComparator)
    {
        Objects.requireNonNull(keyExtractor);
        Objects.requireNonNull(keyComparator);
        return (ThrowingComparator<T> & Serializable)
            (c1, c2) -> keyComparator.compare(keyExtractor.apply(c1),
                                              keyExtractor.apply(c2));
    }

    /* Calls {@link Comparator#comparing} but allows throwing checked exceptions */
    static <T, U extends Comparable<? super U>> ThrowingComparator<T> comparing(
            ThrowingFunction<? super T, ? extends U> keyExtractor)
    {
        Objects.requireNonNull(keyExtractor);
        return (ThrowingComparator<T> & Serializable)
            (c1, c2) -> keyExtractor.apply(c1).compareTo(keyExtractor.apply(c2));
    }

    /* Calls {@link Comparator#comparingInt} but allows throwing checked exceptions */
    static <T> ThrowingComparator<T> comparingInt(ToIntFunction<? super T> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (ThrowingComparator<T> & Serializable)
            (c1, c2) -> Integer.compare(keyExtractor.applyAsInt(c1), keyExtractor.applyAsInt(c2));
    }

    /* Calls {@link Comparator#comparingLong} but allows throwing checked exceptions */
    static <T> ThrowingComparator<T> comparingLong(ToLongFunction<? super T> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (ThrowingComparator<T> & Serializable)
            (c1, c2) -> Long.compare(keyExtractor.applyAsLong(c1), keyExtractor.applyAsLong(c2));
    }

    /* Calls {@link Comparator#comparingDouble} but allows throwing checked exceptions */
    static<T> ThrowingComparator<T> comparingDouble(ToDoubleFunction<? super T> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (ThrowingComparator<T> & Serializable)
            (c1, c2) -> Double.compare(keyExtractor.applyAsDouble(c1), keyExtractor.applyAsDouble(c2));
    }
}
