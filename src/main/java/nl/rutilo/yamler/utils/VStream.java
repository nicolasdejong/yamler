package nl.rutilo.yamler.utils;

import lombok.AllArgsConstructor;
import nl.rutilo.yamler.utils.Tuple.Tuple2;
import nl.rutilo.yamler.utils.Tuple.Tuple3;
import nl.rutilo.yamler.utils.Tuple.Tuple4;
import nl.rutilo.yamler.utils.throwingfunctionals.Throwing2ParamFunction;
import nl.rutilo.yamler.utils.throwingfunctionals.Throwing3ParamFunction;
import nl.rutilo.yamler.utils.throwingfunctionals.Throwing4ParamFunction;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingBiConsumer;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingBiFunction;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingBinaryOperator;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingComparator;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingConsumer;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingFunction;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingIntFunction;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingPredicate;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingRunnable;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingSupplier;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingUnaryOperator;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.BaseStream;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

// TODO: move to collections?
// TODO: Flatmap for arrays?
// TODO: reverse? (should throw for endless streams)
// TODO: join (more direct Collectors?)

/** Wrapper of Stream that uses Value instead of Optional and has (checked) exception handling,
  * meaning that provided lambdas can throw (checked) exceptions and the stream will continue
  * with the option of fallback (mapThrown), throw in the result (when result is not a Value)
  * or have the thrown state in the returned Value (which can throw it again).<br><br>
  *
  * When a Stream is needed, e.g. in a function call, use .toStream() to get the wrapped
  * stream.<br><br>
  *
  * This stream does not implement the Stream interface because the return type for
  * terminating operations is a Value instead of an Optional. When a JDK Stream
  * is required, use {@link #toStream()}.
  * Stream methods that return a different stream type (like an IntStream) are omitted.
  * Use .toStream()... for that as well.<br><br>
  *
  * When an exception occurs when running the stream, the Value (e.g. via findFirst()) will
  * hold the thrown exception. Stream terminators that don't return a Value (like count() or
  * reduce()) will throw the exception immediately.<br><br>
  */
@SuppressWarnings({
  "squid:S1181", // As this is a utility class, support all possible things that can be thrown
  "squid:S3864", // Use stream.peek() for side effects
  "squid:S3973", // if/else chain read better with equal indentation
})
public final class VStream<T> implements AutoCloseable {
    private final Stream<T> stream;
    private final StreamState state;

    @AllArgsConstructor
    private static class StreamState {
        private RuntimeException thrown;
        private boolean          isNullable;
        private StreamState() {}
    }

    private VStream(Stream<T> stream) {
        this(stream, new StreamState());
    }
    private VStream(Stream<T> stream, StreamState state) {
        this.stream = stream;
        this.state = state;
    }

    /** Returns a concatenated stream of the given streams */
    @SafeVarargs // Creating a stream from an array is safe
    public static <U> VStream<U> of(Stream<U>... toConcat) {
        if(toConcat == null || toConcat.length == 0) return empty();
        if(toConcat.length == 1) return toConcat[0] == null ? empty() : new VStream<>(toConcat[0]);
        if(toConcat.length == 2 && toConcat[0] != null && toConcat[1] != null) return of(Stream.concat(toConcat[0], toConcat[1]));
        return Stream.of(toConcat)
                 .filter(Objects::nonNull) // remove null streams
                 .reduce(Stream::concat)
                 .map(VStream::new)
                 .orElseGet(VStream::empty);
    }
    @SafeVarargs // Creating a stream from an array is safe
    public static <U> VStream<U> of(VStream<U>... toConcat) {
        if(toConcat == null || toConcat.length == 0) return empty();
        if(toConcat.length == 1) return toConcat[0] == null ? empty() : of(toConcat[0].stream);
        if(toConcat.length == 2 && toConcat[0] != null && toConcat[1] != null) return of(Stream.concat(toConcat[0].stream, toConcat[1].stream));
        return Stream.of(toConcat)
            .filter(Objects::nonNull) // remove null streams
            .map(vstream -> vstream.stream)
            .reduce(Stream::concat)
            .map(VStream::new)
            .orElseGet(VStream::empty);
    }

    /** Returns a ThrowingStream of the given values */
    @SafeVarargs
    public static <U> VStream<U> of(U... values) {
        return values == null ? empty() : of(Stream.of(values));
    }

    /** Calls {@link #ofCollected(Object[])} for a single value */
    public static VStream<Object> ofCollected(Object valueToConvert) {
        return ofCollected(new Object[] {valueToConvert }); // NOSONAR
    }

    /** Creates a concatenated stream from the given values that, when supported, will be converted to a stream to concat.
      * Otherwise, the value will be added to the stream as-is.<br><br>
      *
      * Types that are recognized:<pre>
      * - null will lead to a null value (which can later be removed via dropNulls())
      * - [Throwing/Int/Long/etcetera]Stream
      * - Collections
      * - Map's entrySet will be streamed as {@link Tuple}s
      * - Arrays
      *
      * For example: ofCollected(List.of(1,2,3), new int[] { 4,5,6 }) leads to 1,2,3,4,5,6
      */
    @SuppressWarnings("unchecked")
    public static VStream<Object> ofCollected(Object... valuesToConvert) {
        final Stream<Object>[] streams = new Stream[valuesToConvert.length];
        for(int i=0; i<valuesToConvert.length; i++) {
            final Stream<Object> toAdd;
            final Object val = valuesToConvert[i];

            if(val == null)               toAdd = Stream.of((Object)null); else
            if(val instanceof VStream)    toAdd = ((VStream<Object>)val).stream; else
            if(val instanceof Stream)     toAdd = (Stream<Object>) val; else
            if(val instanceof BaseStream) toAdd = StreamSupport.stream(Spliterators.spliteratorUnknownSize(((BaseStream<?,?>)val).iterator(), Spliterator.ORDERED), false); else
            if(val instanceof Collection) toAdd = ((Collection<Object>)val).stream(); else
            if(val instanceof Map)        toAdd = ((Map<?,?>)val).entrySet().stream().map(Tuple::of).map(Object.class::cast); else
            if(val.getClass().isArray())  toAdd = ofArray(Object.class, val).stream; else
                                          toAdd = Stream.of(val);
            streams[i] = toAdd;
        }
        return of(streams);
    }

    /** Calls {@link #ofCollected(Object[])} and maps the resulting values. A throwing mapper leads to a null value. */
    public static <U> VStream<U> ofCollectedMapped(ThrowingFunction<Object,U> mapper, Object valueToConvert) {
        return ofCollectedMapped(mapper, new Object[] { valueToConvert }); // NOSONAR
    }

    /** Calls {@link #ofCollected(Object[])} and maps the resulting values. A throwing mapper leads to a null value. */
    public static <U> VStream<U> ofCollectedMapped(ThrowingFunction<Object,U> mapper, Object... valuesToConvert) {
        return ofCollected(valuesToConvert).map(val -> {
            try {
                return mapper.apply(val);
            } catch(final Exception e) {
                return null;
            }
        });
    }

    public static <K,V> VStream<Tuple2<K,V>> ofMap(Map<K,V> map) {
        return of(map.entrySet().stream().map(Tuple::of));
    }

    /** VStream of the elements in an array, with the type of the elements for U */
    public static <U> VStream<U> ofArray(Class<U> type, Object array) {
        @SuppressWarnings("unchecked")
        final U[] uArray = (U[]) Array.newInstance(type, Array.getLength(array));
        for(int i=0; i < uArray.length; i++) {
            //noinspection unchecked
            uArray[i] = (U)Array.get(array, i);
        }
        return of(uArray);
    }
    public static <U> VStream<U> ofArray(U[] array) {
        return of(array);
    }

    /** Returns an empty ThrowingStream */
    public static <U> VStream<U> empty() { return new VStream<>(Stream.empty()); }

    /** Returns this ThrowingStream as a Stream. Previously set operations won't throw. New operations added can throw */
    public Stream<T> toStream() { return stream; }

    @SafeVarargs
    public final VStream<T> concat(VStream<T>... others) {
        @SuppressWarnings("unchecked")
        final Stream<T>[] streams = new Stream[others.length + 1];
        streams[0] = stream;
        for(int i=0; i<others.length; i++) streams[i+1] = others[i].stream;
        return of(streams);
    }

    @SafeVarargs
    public final VStream<T> concat(Stream<T>... others) {
        @SuppressWarnings("unchecked") final Stream<T>[] streams = new Stream[others.length + 1];
        streams[0] = stream;
        System.arraycopy(others, 0, streams, 1, others.length);
        return of(streams);
    }

    /** Join another stream into this stream */
    public <R,O> VStream<R> combine(VStream<O> otherStream, Throwing2ParamFunction<T, O, R> combiner) {
        return combine(this, otherStream, combiner);
    }

    /** Join two other streams into this stream */
    public <R,O,P> VStream<R> combine(VStream<O> otherStream1, VStream<P> otherStream2, Throwing3ParamFunction<T, O, P, R> combiner) {
        return combine(this, otherStream1, otherStream2, combiner);
    }

    /** Join two streams together as one stream using the combiner function */
    // from https://stackoverflow.com/questions/17640754/zipping-streams-using-jdk8-with-lambda-java-util-stream-streams-zip
    public static<A, B, R> VStream<R> combine(VStream<? extends A> a,
                                              VStream<? extends B> b,
                                              Throwing2ParamFunction<? super A, ? super B, ? extends R> combiner) {
        Objects.requireNonNull(combiner);
        final Spliterator<? extends A> aSpliterator = Objects.requireNonNull(a).spliterator();
        final Spliterator<? extends B> bSpliterator = Objects.requireNonNull(b).spliterator();

        // Zipping looses DISTINCT and SORTED characteristics
        final int characteristics = aSpliterator.characteristics() & bSpliterator.characteristics() &
            ~(Spliterator.DISTINCT | Spliterator.SORTED);

        final long zipSize = ((characteristics & Spliterator.SIZED) != 0)
            ? Math.min(aSpliterator.getExactSizeIfKnown(), bSpliterator.getExactSizeIfKnown())
            : -1;

        final Iterator<A> aIterator = Spliterators.iterator(aSpliterator);
        final Iterator<B> bIterator = Spliterators.iterator(bSpliterator);
        final Iterator<R> cIterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return aIterator.hasNext() && bIterator.hasNext();
            }

            @Override
            public R next() {
                return combiner.apply(aIterator.next(), bIterator.next());
            }
        };
        return VStream.of(StreamSupport.stream(
            Spliterators.spliterator(cIterator, zipSize, characteristics),
            a.isParallel() || b.isParallel()
        ));
    }

    /** Join three streams together as one stream using the combiner function */
    public static<A, B, C, R> VStream<R> combine(VStream<? extends A> a,
                                                 VStream<? extends B> b,
                                                 VStream<? extends C> c,
                                                 Throwing3ParamFunction<? super A, ? super B, ? super C, ? extends R> combiner) {
        Objects.requireNonNull(combiner);
        final Spliterator<? extends A> aSpliterator = Objects.requireNonNull(a).spliterator();
        final Spliterator<? extends B> bSpliterator = Objects.requireNonNull(b).spliterator();
        final Spliterator<? extends C> cSpliterator = Objects.requireNonNull(c).spliterator();

        // Zipping looses DISTINCT and SORTED characteristics
        final int characteristics = aSpliterator.characteristics() & bSpliterator.characteristics() & cSpliterator.characteristics() &
            ~(Spliterator.DISTINCT | Spliterator.SORTED);

        final long zipSize = ((characteristics & Spliterator.SIZED) != 0)
            ? Math.min(Math.min(aSpliterator.getExactSizeIfKnown(), bSpliterator.getExactSizeIfKnown()), cSpliterator.getExactSizeIfKnown())
            : -1;

        final Iterator<A> aIterator = Spliterators.iterator(aSpliterator);
        final Iterator<B> bIterator = Spliterators.iterator(bSpliterator);
        final Iterator<C> cIterator = Spliterators.iterator(cSpliterator);
        final Iterator<R> rIterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return aIterator.hasNext() && bIterator.hasNext() && cIterator.hasNext();
            }

            @Override
            public R next() {
                return combiner.apply(aIterator.next(), bIterator.next(), cIterator.next());
            }
        };
        return VStream.of(StreamSupport.stream(
            Spliterators.spliterator(rIterator, zipSize, characteristics),
            a.isParallel() || b.isParallel() || c.isParallel()
        ));
    }

    /** Calls {@link BaseStream#iterator()} of the wrapped stream */
    public Iterator<T> iterator() {
        return stream.iterator();
    }

    /** Calls {@link BaseStream#spliterator()} of the wrapped stream */
    public Spliterator<T> spliterator() {
        return stream.spliterator();
    }

    /** Calls {@link BaseStream#isParallel()} of the wrapped stream */
    public boolean isParallel() {
        return stream.isParallel();
    }

    /** Returns a new ThrowingStream that is sequential. Calls {@link BaseStream#sequential()} of the wrapped stream. */
    public VStream<T> sequential() {
        return new VStream<>(stream.sequential(), state);
    }

    /** Returns a new ThrowingStream that is parallel. Calls {@link BaseStream#parallel()} of the wrapped stream. */
    public VStream<T> parallel() {
        return new VStream<>(stream.parallel(), state);
    }

    /** Returns a new ThrowingStream that is unordered. Calls {@link BaseStream#unordered()} of the wrapped stream. */
    public VStream<T> unordered() {
        return new VStream<>(stream.unordered(), state);
    }

    /** Returns a new ThrowingStream that calls closeHandler when closed. Calls {@link BaseStream#onClose(Runnable)} of the wrapped stream. */
    public VStream<T> onClose(ThrowingRunnable closeHandler) {
        return new VStream<>(stream.onClose(() -> {
            // *do* run the close handlers when the stream crashed -- unlike other operators
            try {
                closeHandler.runThrows();
            } catch(final Exception thrown) {
                if(state.thrown == null) state.thrown = WrappedException.toRuntimeException(thrown);
            }
        }), state);
    }

    /** Calls {@link BaseStream#close()} of the wrapped stream. */
    public void close() {
        stream.close();
    }

    /** Calls the mapper if any of the previous calls lead to the throwing of an exception and resets the thrown state, otherwise no op. */
    public VStream<T> mapThrown(Function<? super Exception, VStream<T>> mapper) {
        final boolean[] mapped = { false };
        final boolean[] wasThrown = { false };
        return new VStream<>(stream
          .flatMap(e -> {
              if(!mapped[0]) {
                  mapped[0] = true;
                  final Exception thrown = state.thrown;
                  if(thrown != null) {
                      state.thrown = null;
                      wasThrown[0] = true;
                      return mapper.apply(WrappedException.unwrap(thrown)).stream;
                  }
              }
              return wasThrown[0] ? null : Stream.of(e);
          }), state);
    }

    /** Alias for {@link Stream#collect}(Collectors.toList()) */
    public List<T> toList() {
        return collect(Collectors.toList());
    }

    /** Alias for {@link Stream#collect}(Collectors.toList()) */
    public Set<T> toSet() {
        return collect(Collectors.toSet());
    }

    /** Alias for {@link Stream#collect}(Collectors.toMap(keyMapper, valueMapper, last wins when duplicate, HashMap)) */
    public <K,V> Map<K,V> toMap(ThrowingFunction<? super T,? extends K> keyMapper, ThrowingFunction<? super T, ? extends V> valueMapper) {
        return collect(Collectors.toMap(keyMapper, valueMapper, (u1, u2) -> u2, HashMap::new));
    }

    /** Auto mapper to map, supporting Tuple2, Map.Entry and array */
    public <K,V> Map<K,V> toMap() {
        return toMap(new HashMap<>());
    }

    /** Auto mapper to map, supporting Tuple2, Map.Entry, List (first & second) and array (first & second) */
    public <K,V> Map<K,V> toMap(Map<K,V> target) { // NOSONAR -- cyclox over multiple inner functions
        @SuppressWarnings("unchecked")
        final Function<T,K> keyMapper = obj -> {
            if(obj instanceof Tuple2) return ((Tuple2<K,V>)obj).a;
            if(obj instanceof Map.Entry) return ((Map.Entry<K,V>)obj).getKey();
            if(obj instanceof List && !((List<K>)obj).isEmpty()) return ((List<K>)obj).get(0);
            if(obj != null && obj.getClass().isArray() && Array.getLength(obj) > 0) return (K)Array.get(obj, 0);
            throw new IllegalArgumentException("Unsupported type for automapper: " + (obj == null ? "[null]" : obj.getClass()));
        };
        @SuppressWarnings("unchecked")
        final Function<T,V> valueMapper = obj -> {
            if(obj instanceof Tuple2) return ((Tuple2<K,V>)obj).b;
            if(obj instanceof Map.Entry) return ((Map.Entry<K,V>)obj).getValue();
            if(obj instanceof List && !((List<V>)obj).isEmpty()) return ((List<V>)obj).get(1);
            if(obj != null && obj.getClass().isArray() && Array.getLength(obj) > 1) return (V)Array.get(obj, 1);
            throw new IllegalArgumentException("Unsupported type for automapper: " + (obj == null ? "[null]" : obj.getClass()));
        };
        return collect(Collectors.toMap(keyMapper, valueMapper, (u1, u2) -> u2, () -> target));
    }

    /** Alias for {@link Stream#collect}(Collectors.toMap(keyMapper, valueMapper, first wins when duplicate, HashMap) */
    public <K,V> Map<K,V> toMapIgnoreDuplicates(ThrowingFunction<? super T,? extends K> keyMapper, ThrowingFunction<? super T, ? extends V> valueMapper) {
        return collect(Collectors.toMap(keyMapper, valueMapper, (u1, u2) -> u1, HashMap::new));
    }

    /** Alias for {@link Stream#collect}(Collectors.toMap(keyMapper,valueMapper), last wins when duplicate, LinkedHashMap) */
    public <K,V> Map<K,V> toOrderedMap(ThrowingFunction<? super T,? extends K> keyMapper, ThrowingFunction<? super T, ? extends V> valueMapper) {
        return collect(Collectors.toMap(keyMapper, valueMapper, (u1, u2) -> u2, LinkedHashMap::new));
    }

    /** Switches result Values to nullable, meaning null values are allowed. By default, null values will lead to absent. */
    public VStream<T> nullable() {
        return new VStream<>(stream, new StreamState(state.thrown, true));
    }
    /** Switches result Values to not-nullable (which is default), meaning null values lead to absent. */
    public VStream<T> notNullable() {
        return new VStream<>(stream, new StreamState(state.thrown, false));
    }

    /** Returns a new ThrowingStream where all null values are removed from stream */
    public VStream<T> dropNulls() {
        return keepWhen(Objects::nonNull);
    }

    /** Alias for filter() */
    public VStream<T> keepWhen(ThrowingPredicate<? super T> predicate) {
        return filter(predicate);
    }

    /** Alias for reversed filter() */
    public VStream<T> dropWhen(ThrowingPredicate<? super T> predicate) {
        return filter(predicate.negate());
    }

    /** Returns a new ThrowingStream with {@link Stream#filter} added where the lambda is allowed to throw checked exceptions. */
    public VStream<T> filter(ThrowingPredicate<? super T> predicate) {
        if(predicate == null) return this;
        return new VStream<>(stream.filter(val -> {
            if(state.thrown != null) return true;
            try {
                return predicate.test(val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return true;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#map} added where the lambda is allowed to throw checked exceptions. */
    public <R> VStream<R> map(ThrowingFunction<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return new VStream<>(stream.map(val -> {
            if(state.thrown != null) return null;
            try {
                return mapper.apply(val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return null;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#map} added where the lambda is allowed to throw checked exceptions. */
    @SuppressWarnings("unchecked")
    public <R,A,B> VStream<R> map(Class<A> aType, Class<B> bType, Throwing2ParamFunction<? super A, ? super B, R> mapper) {
        Objects.requireNonNull(mapper);
        return new VStream<>(stream.map(val -> {
            if(state.thrown != null) return null;
            try {
                if(val instanceof Map.Entry) return mapper.apply( ((Map.Entry<A,B>)val).getKey(), ((Map.Entry<A,B>)val).getValue() );
                if(val instanceof Tuple2) return mapper.apply( ((Tuple2<A,B>)val).a, ((Tuple2<A,B>)val).b );
                throw new IllegalArgumentException("Unsupported grouping argument given: " + val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return null;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#map} added where the lambda is allowed to throw checked exceptions. */
    @SuppressWarnings("unchecked")
    public <R,A,B,C> VStream<R> map(Class<A> aType, Class<B> bType, Class<C> cType, Throwing3ParamFunction<? super A, ? super B, ? super C, R> mapper) {
        Objects.requireNonNull(mapper);
        return new VStream<>(stream.map(val -> {
            if(state.thrown != null) return null;
            try {
                if(val instanceof Map.Entry) return mapper.apply( ((Map.Entry<A,B>)val).getKey(), ((Map.Entry<A,B>)val).getValue(), null );
                if(val instanceof Tuple2) return mapper.apply( ((Tuple2<A,B>)val).a, ((Tuple2<A,B>)val).b, null );
                if(val instanceof Tuple3) return mapper.apply( ((Tuple3<A,B,C>)val).a, ((Tuple3<A,B,C>)val).b, ((Tuple3<A,B,C>)val).c );
                throw new IllegalArgumentException("Unsupported grouping argument given: " + val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return null;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#map} added where the lambda is allowed to throw checked exceptions. */
    @SuppressWarnings("unchecked")
    public <R,A,B,C,D> VStream<R> map(Class<A> aType, Class<B> bType, Class<C> cType, Class<D> dType, Throwing4ParamFunction<? super A, ? super B, ? super C, ? super D, R> mapper) {
        Objects.requireNonNull(mapper);
        return new VStream<>(stream.map(val -> {
            if(state.thrown != null) return null;
            try {
                if(val instanceof Map.Entry) return mapper.apply( ((Map.Entry<A,B>)val).getKey(), ((Map.Entry<A,B>)val).getValue(), null, null );
                if(val instanceof Tuple2) return mapper.apply( ((Tuple2<A,B>)val).a, ((Tuple2<A,B>)val).b, null, null );
                if(val instanceof Tuple3) return mapper.apply( ((Tuple3<A,B,C>)val).a, ((Tuple3<A,B,C>)val).b, ((Tuple3<A,B,C>)val).c, null );
                if(val instanceof Tuple4) return mapper.apply( ((Tuple4<A,B,C,D>)val).a, ((Tuple4<A,B,C,D>)val).b, ((Tuple4<A,B,C,D>)val).c, ((Tuple4<A,B,C,D>)val).d );
                throw new IllegalArgumentException("Unsupported grouping argument given: " + val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return null;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#flatMap} added where the lambda is allowed to throw checked exceptions. */
    public <R> VStream<R> flatMap(ThrowingFunction<? super T, ? extends Stream<? extends R>> mapper) {
        Objects.requireNonNull(mapper);
        return new VStream<>(stream.flatMap(val -> {
            if(state.thrown != null) return null;
            try {
                return mapper.apply(val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return null;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#distinct} added. */
    public VStream<T> distinct() {
        return new VStream<>(stream.distinct(), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#sorted} added. */
    public VStream<T> sorted() {
        return new VStream<>(stream.sorted(), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#sorted} added where the lambda is allowed to throw checked exceptions. */
    public VStream<T> sorted(ThrowingComparator<? super T> comparator) {
        if(comparator == null) return this;
        return new VStream<>(stream.sorted((a, b) -> {
            if(state.thrown != null) return 0;
            try {
                return comparator.compare(a, b);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return 0;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#peek} added where the lambda is allowed to throw checked exceptions. */
    public VStream<T> peek(ThrowingConsumer<? super T> action) {
        if(action == null) return this;
        return new VStream<>(stream.peek(val -> {
            if(state.thrown != null) return;
            try {
                action.accept(val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#limit} added. */
    public VStream<T> limit(long maxSize) {
        return new VStream<>(stream.limit(maxSize), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#skip} added. */
    public VStream<T> skip(long n) {
        return new VStream<>(stream.skip(n), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#takeWhile} added where the lambda is allowed to throw checked exceptions. */
    public VStream<T> takeWhile(ThrowingPredicate<? super T> predicate) {
        if(predicate == null) return this;
        return new VStream<>(stream.takeWhile(val -> {
            if(state.thrown != null) return false;
            try {
                return predicate.test(val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return false;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#dropWhile} added where the lambda is allowed to throw checked exceptions. */
    public VStream<T> dropWhile(ThrowingPredicate<? super T> predicate) {
        if(predicate == null) return this;
        return new VStream<>(stream.dropWhile(val -> {
            if(state.thrown != null) return false;
            try {
                return predicate.test(val);
            } catch(final Exception thrown) {
                state.thrown = WrappedException.toRuntimeException(thrown);
                return false;
            }
        }), state);
    }

    /** Returns a new ThrowingStream with {@link Stream#forEach} added where the lambda is allowed to throw checked exceptions. */
    public void forEach(ThrowingConsumer<? super T> action) {
        if(action == null) return;
        stream.forEach(e -> {
            if(state.thrown != null) throw state.thrown;
            try {
                action.accept(e);
            } catch(final Exception thrown) {
                throw WrappedException.toRuntimeException(thrown);
            }
        });
    }

    /** Returns a new ThrowingStream with {@link Stream#forEachOrdered} added where the lambda is allowed to throw checked exceptions. */
    public void forEachOrdered(ThrowingConsumer<? super T> action) {
        if(action == null) return;
        stream.forEachOrdered(e -> {
            if(state.thrown != null) throw state.thrown;
            try {
                action.accept(e);
            } catch(final Exception thrown) {
                throw WrappedException.toRuntimeException(thrown);
            }
        });
    }

    private Stream<T> streamCheckThrown() {
        return stream.peek(n -> { if(state.thrown != null) throw state.thrown; });
    }

    /** Calls {@link Stream#toArray} and returns an array containing the elements of this stream */
    public Object[] toArray() {
        return streamCheckThrown().toArray();
    }

    /** Calls {@link Stream#toArray(IntFunction)} and returns an array containing the elements of this stream */
    public <A> A[] toArray(ThrowingIntFunction<A[]> generator) {
        //noinspection SuspiciousToArrayCall -- false positive
        return streamCheckThrown().toArray(generator);
    }

    /** Returns a new ThrowingStream with {@link Stream#reduce(Object, BinaryOperator)} added where the lambda is allowed to throw checked exceptions. */
    public T reduce(T identity, ThrowingBinaryOperator<T> accumulator) {
        if(accumulator == null) return identity;
        return stream.reduce(identity, (result, val) -> {
            if(state.thrown != null) throw state.thrown;
            return accumulator.apply(result, val);
        });
    }

    /** Returns a new ThrowingStream with {@link Stream#reduce(BinaryOperator)} added where the lambda is allowed to throw checked exceptions. */
    public Value<T> reduce(ThrowingBinaryOperator<T> accumulator) {
        if(accumulator == null) return Value.empty();
        try {
            return Value.ofOptional(stream.reduce((result, val) -> {
                if(state.thrown != null) throw state.thrown;
                return accumulator.apply(result, val);
            }));
        } catch(final NullPointerException npe) {
            return Value.of(null, state.isNullable);
        } catch(final Exception thrown) {
            return Value.ofThrown(thrown);
        }
    }

    /** Returns a new ThrowingStream with {@link Stream#reduce(Object, BiFunction, BinaryOperator)} added where the lambda is allowed to throw checked exceptions. */
    public <U> U reduce(U identity,
                 ThrowingBiFunction<U, ? super T, U> accumulator,
                 ThrowingBinaryOperator<U> combiner) {
        if(accumulator == null || combiner == null) return identity;

        return stream.reduce(
          identity,
          (result, val) -> {
              if(state.thrown != null) throw state.thrown;
              return accumulator.apply(result, val);
          },
          (a,b) -> {
              if(state.thrown != null) throw state.thrown;
              return combiner.apply(a,b);
          });
    }

    /** Calls {@link Stream#collect(Supplier, BiConsumer, BiConsumer)} where lambdas are allowed to throw checked exceptions. */
    public <R> R collect(ThrowingSupplier<R> supplier,
                         ThrowingBiConsumer<R, ? super T> accumulator,
                         ThrowingBiConsumer<R, R> combiner) {
        Objects.requireNonNull(supplier);
        if(accumulator == null || combiner == null) return supplier.get();

        return stream.collect(
          supplier,
            (r,t) -> {
                if(state.thrown != null) throw state.thrown;
                accumulator.accept(r,t);
            }, (r,t) -> {
                if(state.thrown != null) throw state.thrown;
                combiner.accept(r,t);
            });
    }

    /** Calls {@link Stream#collect(Collector)} where lambdas are allowed to throw checked exceptions. */
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return stream.collect(Collector.of(
          collector.supplier(),
          (a, b) -> {
              if(state.thrown != null) throw state.thrown;
              collector.accumulator().accept(a, b);
          },
          collector.combiner(),
          collector.finisher(),
          collector.characteristics().toArray(new Collector.Characteristics[0]))
        );
    }

    /** Calls {@link Stream#min} where lambdas are allowed to throw checked exceptions. */
    public Value<T> min(ThrowingComparator<? super T> comparator) {
        return Value.supplyOptional(() -> stream.min((a, b) -> {
            if(state.thrown != null) throw state.thrown;
            return comparator.compare(a, b);
        }));
    }

    /** Calls {@link Stream#max} where lambdas are allowed to throw checked exceptions. */
    public Value<T> max(ThrowingComparator<? super T> comparator) {
        return Value.supplyOptional(() -> stream.max((a, b) -> {
            if(state.thrown != null) throw state.thrown;
            return comparator.compare(a, b);
        }));
    }

    /** Calls {@link Stream#count} */
    public long count() {
        return stream.peek(obj -> { if(state.thrown != null) throw state.thrown; }).count();
    }

    /** Calls {@link Stream#anyMatch} where lambdas are allowed to throw checked exceptions. */
    public boolean anyMatch(ThrowingPredicate<? super T> predicate) {
        return stream.anyMatch(a -> {
            if(state.thrown != null) throw state.thrown;
            return predicate.test(a);
        });
    }

    /** Calls {@link Stream#allMatch} where lambdas are allowed to throw checked exceptions. */
    public boolean allMatch(ThrowingPredicate<? super T> predicate) {
        return stream.allMatch(a -> {
            if(state.thrown != null) throw state.thrown;
            return predicate.test(a);
        });
    }

    /** Calls {@link Stream#noneMatch} where lambdas are allowed to throw checked exceptions. */
    public boolean noneMatch(ThrowingPredicate<? super T> predicate) {
        return stream.noneMatch(a -> {
            if(state.thrown != null) throw state.thrown;
            return predicate.test(a);
        });
    }

    /**
     * Returns a {@link Value} describing the first element of this stream,
     * or an empty {@code Value} if the stream is empty.  If the stream has
     * no encounter order, then any element may be returned.
     *
     * <p>This is a short-circuiting terminal operation</p>.
     *
     * <p>If you prefer an Optional, use {@code toStream().findFirst()} instead.
     * Note that it may throw a NullPointerException if the element happens to
     * be null as an Optional, unlike Value, can't contain a null.</p>
     *
     * @return a {@code Value} describing the first element of this stream,
     * or an empty {@code Value} if the stream is empty
     */
    public Value<T> findFirst() {
        final Value<T> first = Value.supplyOptional(state.isNullable, () -> toStream().findFirst());
        return state.thrown != null
          ? Value.ofThrown(state.thrown)
          : first;
    }

    /**
     * Returns a {@link Value} describing some element of the stream, or an
     * empty {@code Value} if the stream is empty.
     *
     * <p>This is a short-circuiting terminal operation</p>.
     *
     * <p>If you prefer an Optional, use {@code toStream().findFirst()} instead.
     * Note that it may throw a NullPointerException if the element happens to
     * be null as an Optional, unlike Value, can't contain a null.</p>
     *
     * @return a {@code Value} describing some element of this stream,
     * or an empty {@code Value} if the stream is empty
     * @see #findFirst()
     */
    public Value<T> findAny() {
        final Value<T> any = Value.supplyOptional(() -> toStream().findAny());
        return state.thrown != null
          ? Value.ofThrown(state.thrown)
          : any;
    }

    /** Returns a new ThrowingStream for {@link Stream#iterate(Object, UnaryOperator)} where the lambdas are allowed to throw checked exceptions. */
    public static<T> VStream<T> iterate(final T seed, final ThrowingUnaryOperator<T> f) {
        return new VStream<>(Stream.iterate(seed, f));
    }

    /** Returns a new ThrowingStream for {@link Stream#iterate(Object, Predicate, UnaryOperator)} where the lambdas are allowed to throw checked exceptions. */
    public static<T> VStream<T> iterate(T seed, ThrowingPredicate<? super T> hasNext, ThrowingUnaryOperator<T> next) {
        return new VStream<>(Stream.iterate(seed, hasNext, next));
    }

    /** Returns a new ThrowingStream for {@link Stream#generate(Supplier)} where the lambdas are allowed to throw checked exceptions. */
    public static<T> VStream<T> generate(ThrowingSupplier<? extends T> s) {
        return new VStream<>(Stream.generate(s));
    }
}