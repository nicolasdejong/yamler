package nl.rutilo.yamler.utils;

import lombok.EqualsAndHashCode;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingBiConsumer;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingBiFunction;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingConsumer;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingFunction;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingPredicate;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingRunnable;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingSupplier;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingTriFunction;

import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Value holds any value that can be absent.<br>
  * A Value can be one of three kinds:<pre>
  * - a value (present)
  * - no value (absent), no reason given
  * - no value (absent), a reason given (Exception)
  * </pre>
  *
  * A Value is much like an Optional in that it can hold a value or be absent (or 'empty' in Optional speak).
  * Unlike Optional, Value *can* hold null values. Because in 99% of the cases a null value is unwanted,
  * the default is a non-nullable Value. To make a Value nullable, create it using {@link #ofNullable} or
  * change an existing Value using {@link #nullable}. The default way of creating is Value is using {@link #of}
  * which is an alias of {@link #ofNotNullable}. To make a nullable Value notNullable again, call {@link #notNullable()}.
  * The nullable/notNullable attribute will be remembered when mapping. So when the result of a map is null,
  * in the nullable case the result will be a Value(null), in the notNullable case the result will be Value([absent]).
  * <br><br>
  *
  * To create a Value, use {@link #of(Object)}, {@link #supply(ThrowingSupplier)} or {@link #supplyValue(ThrowingSupplier)}.
  * The ThrowingSuppliers make it possible to throw (checked) exceptions in the supply leading to an absent Value
  * (with reason).<br><br>
  *
  * The 'no value' situation is encoded as 'isAbsent' (or 'isEmpty' to stay similar to Optional).<br>
  * A 'no value' Value can be created from {@link Value#absent()} (or {@link Value#empty()}).<br><br>
  *
  * The 'reason given' situation is encoded as an exception (both checked and unchecked).<br>
  * A 'no value with reason' Value can be created via {@link Value#ofThrown(Exception)}.<br>
  * Alternatively, throwing inside {@link Value#supply(ThrowingSupplier)} also leads to a thrown Value.<br><br>
  *
  * An Exception is thrown when something exceptional happens, like a database that no longer exists.
  * When asking for something that does not exist, like a record with a non-existing ID, an absent value should be returned.<br><br>
  *
  * Differences with Optional:<ul>
  *  <li> Null can be a legal value when nullable. In that case Value.ofNullable(null) is present and
  *       get() will return null.
  *  <li> Adding {@link #notNullable} or create with {@link #of(Object)} will make behaviour like Optional.
  *  <li> {@link Value#ofNullable(Object)} has different semantics compared with {@link Optional#ofNullable(Object)}.
  *       In Optional the name means that the *input* value can be null which will lead to isEmpty.
  *       In Value the name means that the *result* is a nullable value.
  *  <li> Any exception thrown in (flat)map/filter will lead to absent() while isThrown() is true.
  *       So here suppliers / predicates that throw (checked) exceptions will be accepted without
  *       the need to add try/catch blocks.
  *  <li> The (flat)mapOrThrow / filterOrThrow behave like the Optional map / filter equivalents.
  *       So here suppliers / predicates can be provided that do NOT throw checked exceptions, unlike the
  *       non-orThrow methods that do accept them and the thrown exception will not be caught by Value
  *  <li> Several convenience methods were added to create or combine Value instances.
  *  <li> 'absent' exists as it is symmetrical with 'present'. isEmpty() is an alias of isAbsent() to
  *       stay compatible with Optional.
  *  <li> Comparing two Values with the same absent/value may still result false because the comparison
  *       is of the whole state, which is: value, isAbsent, thrown reason, isNullable.
  *  <li> For checking the inner value, use {@link #is(ThrowingPredicate)}/{@link #is(Object)} or {@link #isAnyOf}
  */
@SuppressWarnings("unchecked")
@EqualsAndHashCode
public final class Value<T> {
    private static final Value<?> ABSENT = new Value<>(null, false, false, null);
    private static final Value<?> NULL   = new Value<>(null, true, true, null);

    private final T value; // NOSONAR -- this is the actual value of the generic Value class
    private final boolean hasValue;
    private final Exception thrown;
    private final boolean isNullable;

    private Value(Exception thrown) { this(null, false, false, thrown); }
    private Value(T value, boolean hasValue, boolean isNullable, Exception thrown) {
        this.value = value;
        this.hasValue = hasValue;
        this.thrown = thrown;
        this.isNullable = isNullable;
    }

    private T getInnerValue() { return value; }

    public String       toString() {
        return getClass().getSimpleName() + "(" + (
          isPresent() ? toString(getInnerValue()) :
          isThrown() ? getThrown().map(t ->
                "[absent because " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ":" + t.getMessage())).orElse("?") + "]"
              : "[absent]")
          + ")";
    }

    /** Returns an optional that will hold the same value as this Value, except null which leads to empty. */
    public Optional<T>  toOptional() { return isPresent() ? Optional.ofNullable(getInnerValue()) : Optional.empty(); }

    public boolean      isPresent() { return hasValue; }
    public boolean      isAbsent() { return !hasValue; }
    public boolean      isEmpty() { return isAbsent(); }
    public boolean      isThrown() { return thrown != null; }
    public boolean      isThrown(Class<? extends Exception> type) { return getThrown(type).isPresent(); }
    public boolean      isNullable() { return isNullable; }
    public T            get() { return orElseThrow(); }

    /** Alias for {@link #ifPresent}: action will be called with the current value if a value is present */
    public Value<T>     peek(ThrowingConsumer<? super T> action) { return ifPresent(action); }
    public Value<T>     ifPresent(ThrowingConsumer<? super T> action) {
        if (isPresent()) action.accept(getInnerValue());
        return this;
    }
    public <U> Value<T> ifBothPresent(Value<U> other, ThrowingBiConsumer<? super T, ? super U> handler) {
        filter(a -> other.isPresent()).ifPresent(val -> handler.accept(val, other.getInnerValue()));
        return this;
    }
    public Value<T>     ifPresentOrElse(ThrowingConsumer<? super T> action, ThrowingRunnable absentAction) {
        if (isPresent()) action.accept(getInnerValue()); else absentAction.run();
        return this;
    }
    public Value<T>     ifAbsent(ThrowingRunnable action) {
        if (!isPresent()) action.run();
        return this;
    }
    public Value<T>     ifEmpty(ThrowingRunnable action) { return ifAbsent(action); }
    public Value<T>     ifThrown(ThrowingConsumer<? super Exception> exceptionConsumer) {
        if(isThrown()) exceptionConsumer.accept(getThrown().get());
        return this;
    }
    public Value<T>     ifThrown(Class<? extends Exception> thrownType, ThrowingConsumer<? super Exception> exceptionConsumer) {
        if(isThrown(thrownType)) exceptionConsumer.accept(getThrown().get());
        return this;
    }
    public Value<T>     ifNull(ThrowingRunnable action) {
        if (!isAbsent() && isNullable() && getInnerValue() == null) action.run();
        return this;
    }
    public Value<T>     ifIs(ThrowingPredicate<? super T> predicate, ThrowingConsumer<? super T> action) {
        if(is(predicate)) action.accept(getInnerValue());
        return this;
    }
    public Value<T>     ifIs(T valueToTest, ThrowingConsumer<? super T> action) {
        if(is(valueToTest)) action.accept(getInnerValue());
        return this;
    }
    public Value<T>     ifIsAnyOf(T[] valuesToTest, ThrowingConsumer<? super T> action) {
        if(isAnyOf(valuesToTest)) action.accept(getInnerValue());
        return this;
    }
    public Value<T>     ifIsAnyOf(Collection<T> valuesToTest, ThrowingConsumer<? super T> action) {
        if(isAnyOf(valuesToTest)) action.accept(getInnerValue());
        return this;
    }

    public Value<T>     filter(ThrowingPredicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        try {
            return isPresent() && predicate.test(getInnerValue()) ? this : absent();
        } catch(final Exception t) { // NOSONAR
            return new Value<>(WrappedException.unwrap(t));
        }
    }
    public Value<T>     filterOrThrow(ThrowingPredicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return isPresent() && predicate.test(getInnerValue()) ? this : absent();
    }

    /** Alias to .filter(predicate).isPresent() */
    public boolean      is(ThrowingPredicate<? super T> predicate) {
        return filter(predicate).isPresent();
    }
    /** Alias to .is(value -> Objects.equals(value, valueToTest)). Different from {@link #equals} because that compares
      * the whole Value state instead of just the value it holds and not compare if absent.
      */
    public boolean      is(T valueToTest) {
        return is(val -> Objects.equals(val, valueToTest));
    }
    public boolean      isNull() {
        return isPresent() && getInnerValue() == null;
    }
    /** Returns true if any of the calls to is(valueToTest) for each of the given values returns true */
    public boolean      isAnyOf(T... valuesToTest) {
        if(isAbsent() || valuesToTest == null) return false;
        for(final T valueToTest : valuesToTest) if(is(valueToTest)) return true;
        return false;
    }
    /** Returns true if valuesToTest contains this value */
    public boolean      isAnyOf(Collection<T> valuesToTest) {
        if(valuesToTest == null) return false;
        return is(valuesToTest::contains);
    }

    public <U> Value<U> map(ThrowingFunction<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        try {
            return isPresent() ? Value.of(mapper.apply(getInnerValue()), isNullable()) : absent();
        } catch(final Exception t) { // NOSONAR
            return new Value<>(WrappedException.unwrap(t));
        }
    }
    public <U> Value<U> mapOrThrow(ThrowingFunction<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return isPresent() ? Value.of(mapper.apply(getInnerValue()), isNullable()) : absent();
    }
    @SafeVarargs
    public final <U> Value<U> mapToFirstPresent(ThrowingFunction<? super T, Value<U>>... mappers) {
        return isAbsent() ? absent() : VStream.of(mappers)
          .map(mapper -> mapper.apply(getInnerValue()))
          .filter(Objects::nonNull)
          .filter(Value::isPresent) // we don't care about reasons for absence
          .findFirst()
          .orElse(Value.absent());
    }
    public <U,B> Value<U> combine(Value<B> otherValue, ThrowingBiFunction<T,B,U> mapper) {
        Objects.requireNonNull(mapper);
        try {
            return isPresent() && (otherValue != null && otherValue.isPresent()) ? Value.of(mapper.apply(getInnerValue(), otherValue.getInnerValue()), isNullable()) : absent();
        } catch(final Exception t) { // NOSONAR
            return ofThrown(t);
        }
    }
    public <U,B,C> Value<U> combine(Value<B> valueB, Value<C> valueC, ThrowingTriFunction<T,B,C,U> mapper) {
        Objects.requireNonNull(mapper);
        try {
            return isPresent() && valueB != null && valueB.isPresent() && valueC != null && valueC.isPresent()
                   ? Value.of(mapper.apply(getInnerValue(), valueB.getInnerValue(), valueC.getInnerValue()), isNullable())
                   : absent();
        } catch(final Exception t) { // NOSONAR
            return ofThrown(t);
        }
    }

    public <U> Value<U> flatMap(ThrowingFunction<? super T, ? extends Value<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        try {
            return flatMapOrThrow(mapper);
        } catch(final Exception t) { // NOSONAR
            return new Value<>(WrappedException.unwrap(t));
        }
    }
    public <U> Value<U> flatMapOrThrow(ThrowingFunction<? super T, ? extends Value<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return isAbsent() ? absent() : Value.of((Value<U>) mapper.apply(getInnerValue()), isNullable()).orElse(absent());
    }
    public <U> Value<U> flatMapOptional(ThrowingFunction<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        try {
            return isAbsent() ? absent() : Value.of((Optional<U>) mapper.apply(getInnerValue())).map(Value::ofOptional).orElse(absent());
        } catch(final Exception t) { // NOSONAR
            return new Value<>(WrappedException.unwrap(t));
        }
    }

    public Value<T>     mapAbsent(ThrowingSupplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return isPresent() ? this : Value.of(supplier.get(), isNullable());
    }
    public Value<T>     mapEmpty(ThrowingSupplier<? extends T> supplier) { return mapAbsent(supplier); }

    public Value<T>     mapThrown(ThrowingFunction<? super Exception, ? extends T> mapper) {
        Objects.requireNonNull(mapper);
        return isThrown() ? getThrown().nullable(isNullable()).map(mapper) : this;
    }
    /** Throws any exception thrown before in the chain. Since it is unknown what kind of exception, it is checked. */
    public Value<T>     throwThrown() throws Exception { // NOSONAR
        if(thrown != null) throw WrappedException.unwrap(thrown);
        return this;
    }
    /** Throws any exception thrown before in the chain, wrapped if it is a checked exception */
    public Value<T>     throwThrownUnchecked() { // NOSONAR
        if(thrown != null) throw WrappedException.toRuntimeException(thrown);
        return this;
    }
    /** Throws any exception of given type that is thrown before in the chain */
    public <C extends Exception> Value<T> throwThrown(Class<C> type) throws C { // NOSONAR
        final C ex = getThrown(type).orElse(null);
        if(ex != null) throw ex;
        return this;
    }
    public Value<Exception> getThrown() {
        return isThrown() ? Value.ofNotNullable(thrown).map(WrappedException::unwrap) : Value.absent();
    }
    public <C extends Exception> Value<C> getThrown(Class<C> type) {
        return getThrown().filter(t -> type.isAssignableFrom(t.getClass())).map(t -> (C)t);
    }

    public Value<T>     not(ThrowingPredicate<? super T> predicate) {
        return filter(ThrowingPredicate.not(predicate));
    }

    /** Alias of {@link #notNullable} */
    public Value<T>     notNull() { return notNullable(); }

    /** Returns a not-nullable value for the current value, unless that value is null which will lead to absent.
      * A mapping result of null will lead to absent.
      */
    public Value<T>     notNullable() { return nullable(false); }

    /** Returns a Value that can hold a null value. So mapping to null will lead to a null value instead of absent */
    public Value<T>     nullable() { return nullable(true); }

    /** Returns a Value that can/cannot hold a null value. When nullable, mapping to null will lead to a null value instead of absent */
    public Value<T>     nullable(boolean setIsNullable) {
        return setIsNullable == isNullable() && (setIsNullable || !isNull()) ? this : new Value<>(getInnerValue(), isPresent() && (setIsNullable || !isNull()), setIsNullable, thrown); }

    /** Returns this value in a Stream of one element, otherwise returns an empty Stream */
    public Stream<T>    stream() { return isPresent() ? Stream.of(getInnerValue()) : Stream.empty(); }

    /** Returns this value in a VStream of one element, otherwise returns an empty VStream */
    public VStream<T>   vstream() { return isPresent() ? VStream.of(getInnerValue()) : VStream.empty(); }

    /** Returns this if present otherwise returns Value.of[Nullable](notNull) (ofNullable is called when nullable) */
    public Value<T> or(T notNull)     { return isPresent() ? this : Value.of(notNull, isNullable()); }
    /** Returns this if present, otherwise returns given other */
    public Value<T> or(Value<T> other) { return isPresent() ? this : other; }
    /** Returns this if present, otherwise returns the supplied value (which can throw leading to a Value.isThrown()). Supplying null leads to absent. */
    public Value<T> orSupply(ThrowingSupplier<T> supplier) {
        Objects.requireNonNull(supplier);
        try {
            return isPresent() ? this : Value.of(supplier.get(), isNullable());
        } catch(final Exception t) { // NOSONAR
            return new Value<>(WrappedException.unwrap(t));
        }
    }
    /** Returns this if present, otherwise returns the supplied value (or throws if the supplier throws). Supplying null leads to absent. */
    public Value<T> orSupplyOrThrow(ThrowingSupplier<T> supplier) {
        Objects.requireNonNull(supplier);
        return isPresent() ? this : Value.of(supplier.get(), isNullable());
    }
    public Value<T> orSupplyValue(ThrowingSupplier<? extends Value<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        try {
            return isPresent() ? this : Value.ofNotNullable((Value<T>)supplier.get()).orElse(absent());
        } catch(final Exception t) { // NOSONAR
            return new Value<>(WrappedException.unwrap(t));
        }
    }
    /** Returns this if present, otherwise returns the supplied value (or throws if the supplier throws). Supplying null leads to absent. */
    public Value<T> orSupplyValueOrThrow(ThrowingSupplier<? extends Value<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        return isPresent() ? this : Value.ofNotNullable((Value<T>)supplier.get()).orElse(absent());
    }
    /** Returns this value if present, the given other value if not present */
    public T            orElse(T other) { return isPresent() ? getInnerValue() : other; }
    /** Returns this value if present, otherwise returns the result of the supplier */
    public T            orElseGet(ThrowingSupplier<? extends T> supplier) { return isPresent() ? getInnerValue() : supplier.get(); }
    /** Returns this value if present, otherwise throws a NoSuchElementException */
    public T            orElseThrow() {
        if (isAbsent()) throw new NoSuchElementException("No value present");
        return getInnerValue();
    }
    /** Returns this value if present, otherwise throws the exception as supplied */
    public <X extends Exception> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if(isAbsent()) throw exceptionSupplier.get();
        return getInnerValue();
    }

    /** Returns a Value that holds no actual value but isAbsent (opposite of isPresent) */
    public static <T> Value<T> absent()          { return (Value<T>) ABSENT; }

    /** Alias for absent() to stay similar to Optional which only has empty() meaning absent. */
    public static <T> Value<T> empty()           { return absent(); }

    /** Alias for {@link #ofNotNullable} as that is the case the value is most often used for  */
    public static <T> Value<T> of(T valueOrNull) { return ofNotNullable(valueOrNull); }

    /** Calls {@link #ofNullable} when isNullable is true, otherwise calls {@link #ofNotNullable} */
    public static <T> Value<T> of(T valueOrNull, boolean isNullable) {
        return isNullable ? ofNullable(valueOrNull) : ofNotNullable(valueOrNull);
    }

    /** Returns a Value that is present for the given object, even if it is null.
      * Also, if a mapper returns null, the value will set to null. If the Value
      * should no longer be nullable, call {@link #notNullable} on the value.<br><br>
      *
      * This call may be confusing compared with {@link Optional#ofNullable} as that
      * means that a null value will lead to empty. Here it means that a value can
      * be null.<br><br>
      *
      * The alternative call is {@link #ofNotNullable} which creates a Value which
      * is absent when a null value is provided.
      */
    public static <T> Value<T> ofNullable(T valueOrNull) { return valueOrNull == null ? ofNull() : new Value<>(valueOrNull, /*isPresent=*/true, /*isNullable=*/true, null); }

    /** Returns a Value that is present for the given object, unless it is null,
      * which will lead to absent.
      * If a mapper returns null, the value will become absent. If the Value
      * should become nullable again, call {@link #nullable} on the value.<br><br>
      *
      * This call may be confusing compared with {@link Optional#ofNullable} as that
      * means that a null value will lead to empty. Here it means that a value can
      * be null.<br><br>
      *
      * The alternative call is {@link #ofNullable} which creates a Value which
      * holds a null value when a null value is provided.
      */
    public static <T> Value<T> ofNotNullable(T valueOrNull) { return valueOrNull == null ? absent() : new Value<>(valueOrNull, /*isPresent=*/true, /*isNullable=*/false, null); }

    /** Returns a Value that is absent with reason */
    public static <T> Value<T> ofThrown(Exception thrown) {
        return new Value<>(thrown);
    }
    /** Returns a Value with the value null */
    public static <T> Value<T> ofNull() { return (Value<T>) Value.NULL; }
    /** Returns a Value that holds the same value of the given Optional or absent if the Optional is empty or null */
    public static <T> Value<T> ofOptional(Optional<T> opt) {
        return opt != null && opt.isPresent() ? ofNotNullable(opt.orElse(null)) : new Value<>(null, /*isPresent=*/false, /*isNullable=*/false, null); // NOSONAR - util
    }

    /** Creates a Value from supplier which may throw (leading to absent thrown) or return null (leading to absent). */
    public static <T> Value<T> supply(ThrowingSupplier<T> supplier) {
        try {
            return Value.of(supplier.getThrows());
        } catch(final Exception t) { // NOSONAR -- util
            return ofThrown(t);
        }
    }
    /** Creates a Value from supplier which may throw (leading to absent thrown) or return null (leading to a null value). */
    public static <T> Value<T> supplyNullable(ThrowingSupplier<T> supplier) {
        try {
            return Value.ofNullable(supplier.getThrows());
        } catch(final Exception t) { // NOSONAR -- util
            return ofThrown(t);
        }
    }

    /** Creates a Value from supplier which may throw, leading to an absent Value that isThrown. Null Value leads to absent. */
    public static <T> Value<T> supplyValue(ThrowingSupplier<Value<T>> supplier) {
        try {
            final Value<T> value = supplier.getThrows();
            return value == null ? absent() : value;
        } catch(final Exception t) { // NOSONAR -- util
            return ofThrown(t);
        }
    }
    public static <T> Value<T> supplyFirstPresentValue(ThrowingSupplier<Value<T>>... suppliers) {
        return VStream.of(suppliers)
          .map(ThrowingSupplier::get)
          .filter(Value::isPresent) // we don't care about reasons for absence
          .findFirst()
          .orElse(Value.absent());
    }

    /** Creates a Value from supplier (which may throw, leading to absent isThrown).
      * NullPointerException in the Optional code will lead to Value.absent().
      */
    public static <T> Value<T> supplyOptional(ThrowingSupplier<Optional<T>> supplier) {
        return supplyOptional(false, supplier);
    }

    /** Creates a Value from supplier (which may throw, leading to absent isThrown).
      * NullPointerException in the Optional code will lead to Value.ofNullable(null).
      */
    public static <T> Value<T> supplyOptionalAsNullable(ThrowingSupplier<Optional<T>> supplier) {
        return supplyOptional(true, supplier);
    }

    /** Creates a Value from supplier (which may throw, leading to absent isThrown).
      * NullPointerException in the Optional code will lead to Value.ofNull().
      */
    public static <T> Value<T> supplyOptional(boolean isNullable, ThrowingSupplier<Optional<T>> supplier) {
        try {
            return ofOptional(supplier.get());
        } catch(final NullPointerException npe) {
            // Here we need to distinguish if the npe is because of a null value in
            // the Optional or something that happened in the supplier.
            // Null values are allowed in a Value so that should lead to a Value of null,
            // otherwise a Value of the npe is returned.
            return Optional.class.getName().equals(npe.getStackTrace()[1].getClassName()) // a bit brittle but assumes Optional impl doesn't change (much)
                ? Value.of(null, isNullable)
                : Value.ofThrown(npe);
        } catch(final Exception t) { // NOSONAR -- util
            return Value.ofThrown(t);
        }
    }

    /** Returns the first given value that is not null, otherwise throws NullPointerException */
    @SafeVarargs
    public static <T> T or(T... nullables) {
        return Arrays.stream(nullables)
                     .filter(Objects::nonNull)
                     .findFirst()
                     .orElseThrow(NullPointerException::new);
    }

    /** Returns the first value that is not absent, absent otherwise */
    @SafeVarargs
    public static <T> Value<T> or(Value<T>... values) {
        return VStream.of(values)
                      .filter(Objects::nonNull)
                      .filter(Value::isPresent)
                      .findFirst()
                      .orElse(Value.absent());
    }

    /** Returns the first value that is not null or thrown, otherwise throws NullPointerException */
    @SafeVarargs
    public static <T> T orSupply(ThrowingSupplier<T>... suppliers) {
        return orSupplyToValue(suppliers)
                      .orElseThrow(NullPointerException::new);
    }

    /** Returns the first value that is not absent, absent otherwise */
    @SafeVarargs
    public static <T> Value<T> orSupplyValue(ThrowingSupplier<Value<T>>... valueSuppliers) {
        return VStream.of(valueSuppliers)
                      .filter(Objects::nonNull)
                      .map(Supplier::get)
                      .filter(Objects::nonNull)
                      .filter(Value::isPresent)
                      .findFirst()
                      .orElse(Value.absent());
    }
    @SafeVarargs
    public static <T> Value<T> orSupplyValueExtends(ThrowingSupplier<Value<? extends T>>... valueSuppliers) {
        //noinspection unchecked -- If ? extends T than ? is also T
        return (Value<T>)VStream.of(valueSuppliers)
                      .filter(Objects::nonNull)
                      .map(Supplier::get)
                      .filter(Objects::nonNull)
                      .filter(Value::isPresent)
                      .findFirst()
                      .orElse(Value.absent());
    }
    /** Returns the first value that is not null or thrown, absent otherwise */
    @SafeVarargs
    public static <T> Value<T> orSupplyToValue(ThrowingSupplier<T>... suppliers) {
        return VStream.of(suppliers)
                      .filter(Objects::nonNull)
                      .map(Value::supply)
                      .filter(Value::isPresent)
                      .findFirst()
                      .orElse(Value.absent());
    }

    public static <A,B,T> Value<T> combine(Value<A> a, Value<B> b, ThrowingBiFunction<A,B,T> mapper) {
        return a != null && b != null && mapper != null ? a.combine(b, mapper) : Value.absent();
    }
    public static <A,B,C,T> Value<T> combine(Value<A> a, Value<B> b, Value<C> c, ThrowingTriFunction<A,B,C,T> mapper) {
        return a != null && b != null && c != null && mapper != null ? a.combine(b, c, mapper) : Value.absent();
    }

    private static String toString(Object value) {
        return
          value instanceof String ? "\"" + value + "\"" :
          value instanceof byte[] ? toString((byte[])value) :
               String.valueOf(value);
    }
    private static String toString(byte[] data) {
        if(data == null) return "null";
        final int printableAsciiMin = 32;
        final int printableAsciiMax = 126;
        final int maxLength = 40;
        return
          "byte[" + data.length + "]:" +
          VStream.ofCollected(data)
                 .limit(maxLength)
                 .map(b -> (byte)b < printableAsciiMin || (byte)b > printableAsciiMax ? "." : "" + (char)((byte)b))
                 .reduce("", (a,b) -> a + b)
          + (data.length > maxLength ? "[" + (data.length - maxLength) + " more]" : "");
    }
}
