package nl.rutilo.yamler.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({
  "squid:S5778" // allow multiple statements in assertThrows()
  , "MagicNumber"})
class ValueTest {
    private static void notSupposedToGetHere() {
        fail("Not supposed to get here");
    }
    private static RuntimeException getTestIAException() { // not a const because of stack trace
        return new IllegalArgumentException("testing");
    }

    @Test void experiment() {
        record KeyVal(String key, String value) {}
        List.of("a:1", "b:2", "c:3").stream()
          .map(s -> new KeyVal(s.split(":")[0], s.split(":")[1]))
          .filter(kv -> kv.key.equals(kv.value))
          ;
    }


    @Test void testToString() {
        assertThat(Value.empty().toString(), is("Value([absent])"));
        assertThat(Value.of("abc").toString(), is("Value(\"abc\")"));
        assertThat(Value.of("abc").map(a -> { throw getTestIAException(); }).toString(), is("Value([absent because IllegalArgumentException:testing])"));

        final byte[] data = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890_!@#$%^&*()".getBytes(StandardCharsets.UTF_8);
        assertThat(Value.of(data).toString(), is("Value(byte[74]:abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN[34 more])"));
        data[1] = 13;
        data[2] = 0;
        data[3] = 127;
        data[4] = -5;
        assertThat(Value.of(data).toString(), is("Value(byte[74]:a....fghijklmnopqrstuvwxyzABCDEFGHIJKLMN[34 more])"));
    }
    @Test void toOptional() {
        assertThat(Value.empty().toOptional(), is(Optional.empty()));
        assertThat(Value.of("abc").toOptional(), is(Optional.of("abc")));
        assertThat(Value.of(null).toOptional(), is(Optional.empty()));
    }
    @Test void isPresent() {
        assertThat(Value.empty().isPresent(), is(false));
        assertThat(Value.of("abc").isPresent(), is(true));
        assertThat(Value.of(null).isPresent(), is(false));
        assertThat(Value.ofNullable(null).isPresent(), is(true));
    }
    @Test void isEmpty() {
        assertThat(Value.empty().isEmpty(), is(true));
        assertThat(Value.of("abc").isEmpty(), is(false));
        assertThat(Value.of(null).isEmpty(), is(true));
        assertThat(Value.ofNullable(null).isEmpty(), is(false));
    }
    @Test void isAbsent() {
        assertThat(Value.empty().isAbsent(), is(true));
        assertThat(Value.of("abc").isAbsent(), is(false));
        assertThat(Value.of(null).isAbsent(), is(true));
        assertThat(Value.ofNullable(null).isAbsent(), is(false));
    }
    @Test void isThrown() {
        assertThat(Value.ofThrown(new IllegalArgumentException()).isThrown(), is(true));
        assertThat(Value.of(null).isThrown(), is(false));
    }
    @Test void isThrownType() {
        assertThat(Value.ofThrown(new IllegalArgumentException()).isThrown(), is(true));
        assertThat(Value.ofThrown(new IllegalArgumentException()).isThrown(RuntimeException.class), is(true));
        assertThat(Value.ofThrown(new IllegalArgumentException()).isThrown(IOException.class), is(false));
        assertThat(Value.of(null).isThrown(RuntimeException.class), is(false));
    }
    @Test void get() {
        assertThat(Value.of("abc").get(), is("abc"));
        assertThat(Value.of(null).isAbsent(), is(true));
        assertThat(Value.ofNullable(null).get(), is(nullValue()));
        final Value<?> empty = Value.empty();
        assertThrows(NoSuchElementException.class, empty::get);
    }

    @Test void peek() {
        final boolean[] called = { false };
        Value.empty().peek(a -> notSupposedToGetHere());
        Value.of("abc").peek(a -> { called[0] = true; assertThat(a, is("abc")); }); assertThat(called[0], is(true)); called[0] = false;
    }
    @Test void ifPresent() {
        final boolean[] called = { false };
        Value.empty().ifPresent(a -> notSupposedToGetHere());
        Value.of("abc").ifPresent(a -> {called[0] = true; assertThat(a, is("abc")); }); assertThat(called[0], is(true));called[0] = false;
        Value.of(null).ifPresent(a -> notSupposedToGetHere());
        Value.ofNullable(null).ifPresent(a -> {called[0] = true; assertThat(a, is(nullValue())); }); assertThat(called[0], is(true));called[0] = false;
    }
    @Test void ifBothPresent() {
        final boolean[] called = { false };
        Value.empty().ifBothPresent(Value.empty(),   (a, b) -> notSupposedToGetHere());
        Value.empty().ifBothPresent(Value.of("abc"), (a, b) -> notSupposedToGetHere());
        Value.of("abc").ifBothPresent(Value.empty(),   (a, b) -> notSupposedToGetHere());
        Value.of("abc").ifBothPresent(Value.of("def"), (a, b) -> {called[0] = true; assertThat(a, is("abc")); assertThat(b, is("def")); }); assertThat(called[0], is(true));called[0] = false;
        Value.of(null).ifBothPresent(Value.of(null),  (a, b) -> notSupposedToGetHere()); assertThat(called[0], is(false));called[0] = false;
        Value.ofNullable(null).ifBothPresent(Value.ofNullable(null),  (a, b) -> {called[0] = true; assertThat(a, is(nullValue())); assertThat(b, is(nullValue())); }); assertThat(called[0], is(true));called[0] = false;
    }
    @Test void ifPresentOrElse() {
        final boolean[] called = { false };
        Value.empty().ifPresentOrElse(a -> notSupposedToGetHere(), () -> called[0] = true); assertThat(called[0], is(true));called[0] = false;
        Value.of("abc").ifPresentOrElse(a -> {called[0] = true; assertThat(a, is("abc")); }, ValueTest::notSupposedToGetHere); assertThat(called[0], is(true));called[0] = false;
    }
    @Test void ifEmpty() {
        final boolean[] called = { false };
        Value.of("abc").ifEmpty(ValueTest::notSupposedToGetHere);
        Value.empty().ifEmpty(() -> called[0] = true); assertThat(called[0], is(true));called[0] = false;
        Value.of(null).ifEmpty(() -> called[0] = true); assertThat(called[0], is(true)); called[0] = false;
        Value.ofNullable(null ).ifEmpty(ValueTest::notSupposedToGetHere);
    }
    @Test void ifAbsent() {
        final boolean[] called = { false };
        Value.of("abc").ifAbsent(ValueTest::notSupposedToGetHere);
        Value.empty().ifAbsent(() -> called[0] = true); assertThat(called[0], is(true));called[0] = false;
        Value.ofNullable(null).ifAbsent(ValueTest::notSupposedToGetHere);
    }
    @Test void ifThrown() {
        final boolean[] called = { false };
        Value.empty().ifThrown(ex -> notSupposedToGetHere());
        Value.of("abc").ifThrown(ex -> notSupposedToGetHere());
        Value.of("abc").map(a -> { throw getTestIAException(); }).ifThrown(ex -> called[0] = true); assertThat(called[0], is(true));called[0] = false;
    }
    @Test void ifThrownType() {
        final boolean[] called = { false };
        Value.empty().ifThrown(RuntimeException.class, ex -> notSupposedToGetHere());
        Value.of("abc").ifThrown(RuntimeException.class, ex -> notSupposedToGetHere());
        Value.ofThrown(getTestIAException()).ifThrown(IllegalStateException.class, ex -> notSupposedToGetHere());
        Value.ofThrown(getTestIAException()).ifThrown(IllegalArgumentException.class, ex -> called[0] = true); assertThat(called[0], is(true));called[0] = false;
    }
    @Test void ifNull() {
        final boolean[] called = { false };
        Value.empty().ifNull(ValueTest::notSupposedToGetHere);
        Value.ofNullable("a").ifNull(ValueTest::notSupposedToGetHere);
        Value.ofNullable(null).ifNull(() -> called[0] = true); assertThat(called[0], is(true)); called[0] = false;
    }
    @Test void ifIsPredicate() {
        final boolean[] called = { false, false };
        assertThat(Value.absent().ifIs(val -> { notSupposedToGetHere(); return true; }, val -> notSupposedToGetHere()), is(Value.absent()));
        assertThat(Value.of(1).ifIs(val -> { called[0]=true; return true; }, val -> called[1] = true), is(Value.of(1)));
        assertThat(called[0], is(true)); called[0] = false;
        assertThat(called[1], is(true)); called[1] = false;
        assertThat(Value.of(1).ifIs(val -> { called[0]=true; return false; }, val -> notSupposedToGetHere()), is(Value.of(1)));
        assertThat(called[0], is(true)); called[0] = false;
    }
    @Test void ifIs() {
        final boolean[] called = { false };
        assertThat(Value.absent().ifIs(1, val -> notSupposedToGetHere()), is(Value.absent()));
        assertThat(Value.of(1).ifIs(1, val -> called[0] = true), is(Value.of(1))); assertThat(called[0], is(true)); called[0] = false;
        assertThat(Value.of(1).ifIs(2, val -> notSupposedToGetHere()), is(Value.of(1)));
    }
    @Test void ifIsAnyOf() {
        final boolean[] called = { false };
        assertThat(Value.absent().ifIsAnyOf(new Integer[] { 1, 2 }, val -> notSupposedToGetHere()), is(Value.absent()));
        assertThat(Value.of(2).ifIsAnyOf(new Integer[] { 1, 2 }, val -> called[0] = true), is(Value.of(2))); assertThat(called[0], is(true)); called[0] = false;
        assertThat(Value.of(3).ifIsAnyOf(new Integer[] { 1, 2 }, val -> notSupposedToGetHere()), is(Value.of(3)));

        assertThat(Value.absent().ifIsAnyOf(List.of(1, 2), val -> notSupposedToGetHere()), is(Value.absent()));
        assertThat(Value.of(2).ifIsAnyOf(Set.of(1, 2), val -> called[0] = true), is(Value.of(2))); assertThat(called[0], is(true)); called[0] = false;
        assertThat(Value.of(3).ifIsAnyOf(List.of(1, 2), val -> notSupposedToGetHere()), is(Value.of(3)));
    }

    @Test void filter() {
        assertThat(Value.empty().filter(a -> { notSupposedToGetHere(); return true; }).isPresent(), is(false));
        assertThat(Value.of("abc").filter(s -> !s.isEmpty()).get(), is("abc"));
        assertThat(Value.of("abc").filter(String::isEmpty).isPresent(), is(false));
        assertDoesNotThrow(() -> Value.of(123).filter(a -> { throw getTestIAException(); }));
    }
    @Test void filterOrThrow() {
        assertThat(Value.empty().filterOrThrow(a -> { notSupposedToGetHere(); return true; }).isPresent(), is(false));
        assertThat(Value.of("abc").filterOrThrow(s -> !s.isEmpty()).get(), is("abc"));
        assertThat(Value.of("abc").filterOrThrow(String::isEmpty).isPresent(), is(false));
        assertThrows(IllegalArgumentException.class, () -> Value.of(123).filterOrThrow(a -> { throw getTestIAException(); }));
    }
    @Test void isPredicate() {
        assertThat(Value.absent().is(val -> { notSupposedToGetHere(); return true;}), is(false));
        assertThat(Value.of(1).is(val -> val == 1), is(true));
        assertThat(Value.of(1).is(val -> val == 2), is(false));
        assertThat(Value.of(1).is(val -> { throw getTestIAException(); }), is(false));
        assertThat(Value.ofNullable(null).is("a"::equals), is(false));
        assertThat(Value.ofNullable(null).is(Objects::isNull), is(true));
    }
    @Test void isA() {
        assertThat(Value.absent().is(1), is(false));
        assertThat(Value.of(1).is(1), is(true));
        assertThat(Value.of(1).is(2), is(false));
        assertThat(Value.ofNullable(null).is(1), is(false));
    }
    @Test void isNull() {
        assertThat(Value.absent().isNull(), is(false));
        assertThat(Value.of(null).isNull(), is(false));
        assertThat(Value.of(1).isNull(), is(false));
        assertThat(Value.ofNullable(null).isNull(), is(true));
    }
    @Test void isAnyOf() {
        assertThat(Value.absent().isAnyOf(1, 2, 3, 4), is(false));
        assertThat(Value.of(3).isAnyOf(1, 2, 3, 4), is(true));
        assertThat(Value.of(5).isAnyOf(1, 2, 3, 4), is(false));
        assertThat(Value.ofNullable(null).isAnyOf(1, 2, 3, 4), is(false));
        assertThat(Value.ofNullable(null).isAnyOf("a", "b", null, "c"), is(true));

        assertThat(Value.absent().isAnyOf(List.of(1, 2, 3, 4)), is(false));
        assertThat(Value.of(3).isAnyOf(Set.of(1, 2, 3, 4)), is(true));
        assertThat(Value.of(5).isAnyOf(Set.of(1, 2, 3, 4)), is(false));
        final String nullString = null; // null with type
        //noinspection ConstantConditions -- nullString is always null, but it is needed for the type
        assertThat(Value.ofNullable(nullString).isAnyOf(new ArrayList<>() {{ add("a"); add(null); add("c"); }}), is(true));
    }

    @Test void map() {
        assertThat(Value.empty().map(a -> { notSupposedToGetHere(); return null; }).isPresent(), is(false));
        assertThat(Value.ofNullable(123).map(a -> null).isPresent(), is(true));
        assertThat(Value.of(123)        .map(a -> null).isPresent(), is(false));
        assertThat(Value.of(123).map(a -> a + 10).get(), is(133));
        assertDoesNotThrow(() -> Value.of(123).map(a -> { throw getTestIAException(); }));
    }
    @Test void mapOrThrow() {
        assertThat(Value.empty().mapOrThrow(a -> { notSupposedToGetHere(); return null; }).isPresent(), is(false));
        assertThat(Value.ofNullable(123).mapOrThrow(a -> null).isPresent(), is(true));
        assertThat(Value.of(123)        .mapOrThrow(a -> null).isPresent(), is(false));
        assertThat(Value.of(123)        .mapOrThrow(a -> a + 10).get(), is(133));
        assertThrows(Exception.class, () -> Value.of(123).mapOrThrow(a -> { throw getTestIAException(); }));
    }
    @Test void mapToFirstPresent() {
        assertThat(Value.absent().mapToFirstPresent(
          obj -> { notSupposedToGetHere(); return null; },
          obj -> { notSupposedToGetHere(); return null; }
        ), is(Value.absent()));
        assertThat(Value.of("123").mapToFirstPresent(
          obj -> null,
          obj -> Value.absent(),
          obj -> Value.of(123),
          obj -> { notSupposedToGetHere(); return Value.of(456); },
          obj -> Value.of(789)
        ).is(123), is(true));
    }
    @Test void combine() {
        assertThat(Value.empty().combine(Value.empty(), (a, b) -> { notSupposedToGetHere(); return null; }).isEmpty(), is(true));
        assertThat(Value.of("a").combine(Value.empty(), (a, b) -> { notSupposedToGetHere(); return null; }).isEmpty(), is(true));
        assertThat(Value.empty().combine(Value.of("b"), (a, b) -> { notSupposedToGetHere(); return null; }).isEmpty(), is(true));
        assertThat(Value.of("a").combine(Value.of("b"), (a, b) -> { assertThat(a, is("a")); assertThat(b, is("b")); return "c"; }).orElse("z"), is("c"));
        assertThat(Value.of("a").combine(Value.of("b"), (a, b) -> { throw getTestIAException(); }).isThrown(), is(true));

        assertThat(Value.empty().combine(Value.empty(), Value.empty(), (a, b, c) -> { notSupposedToGetHere(); return null; }).isEmpty(), is(true));
        assertThat(Value.empty().combine(Value.of("b"), Value.empty(), (a, b, c) -> { notSupposedToGetHere(); return null; }).isEmpty(), is(true));
        assertThat(Value.empty().combine(Value.empty(), Value.of("c"), (a, b, c) -> { notSupposedToGetHere(); return null; }).isEmpty(), is(true));
        assertThat(Value.of("a").combine(Value.empty(), Value.empty(), (a, b, c) -> { notSupposedToGetHere(); return null; }).isEmpty(), is(true));
        assertThat(Value.of("a").combine(Value.of("b"), Value.empty(), (a, b, c) -> { notSupposedToGetHere(); return null; }).isEmpty(), is(true));
        assertThat(Value.of("a").combine(Value.of("b"), Value.of("c"), (a, b,c) -> {
            assertThat(a, is("a"));
            assertThat(b, is("b"));
            assertThat(c, is("c"));
            return "d";
        }).orElse("z"), is("d"));
        assertThat(Value.of("a").combine(Value.of("b"), Value.of("c"), (a, b,c) -> { throw getTestIAException(); }).isThrown(), is(true));
    }
    @Test void flatMap() {
        assertThat(Value.empty().flatMap(a -> { notSupposedToGetHere(); return null; }).isPresent(), is(false));
        assertThat(Value.of(123).flatMap(a -> null).isPresent(), is(false));
        assertThat(Value.of(123).flatMap(a -> Value.empty()).isPresent(), is(false));
        assertThat(Value.of(123).flatMap(a -> Value.of(a + 10)).get(), is(133));
        assertDoesNotThrow(() -> Value.of(123).flatMap(a -> { throw getTestIAException(); }));
    }
    @Test void flatMapOptional() {
        assertThat(Value.empty().flatMapOptional(a -> { notSupposedToGetHere(); return Optional.empty(); }).isPresent(), is(false));
        //noinspection OptionalAssignedToNull -- testing null handling
        assertThat(Value.of(123).flatMapOptional(a -> null).isPresent(), is(false));
        assertThat(Value.of(123).flatMapOptional(a -> Optional.empty()).isPresent(), is(false));
        assertThat(Value.of(123).flatMapOptional(a -> Optional.of(a + 10)).get(), is(133));
        assertDoesNotThrow(() -> Value.of(123).flatMapOptional(a -> { throw getTestIAException(); }));
    }
    @Test void flatMapOrThrow() {
        assertThat(Value.empty().flatMapOrThrow(a -> { notSupposedToGetHere(); return null; }).isPresent(), is(false));
        assertThat(Value.of(123).flatMapOrThrow(a -> null).isPresent(), is(false));
        assertThat(Value.of(123).flatMapOrThrow(a -> Value.empty()).isPresent(), is(false));
        assertThat(Value.of(123).flatMapOrThrow(a -> Value.of(a + 10)), is(Value.of(133)));
        assertThrows(Exception.class, () -> Value.of(123).flatMapOrThrow(a -> { throw getTestIAException(); }));
    }
    @Test void mapEmpty() {
        assertThat(Value.empty().mapEmpty(() -> 1).get(), is(1));
        assertThat(Value.of(123).mapEmpty(() -> 1).get(), is(123));
    }
    @Test void mapThrown() {
        assertThat(Value.of(123).map(a -> { throw getTestIAException(); }).mapThrown(ex -> {
            assertThat(ex.getClass(), is(IllegalArgumentException.class));
            assertThat(ex.getMessage(), is(getTestIAException().getMessage()));
            return 345;
        }).orElseThrow(), is(345));
        assertThat(Value.of(123).map(a -> a + 10).mapThrown(a -> { notSupposedToGetHere(); return 0; }).get(), is(133));
    }
    @Test void throwThrown() {
        assertThrows(IllegalArgumentException.class, () -> Value.of(123).map(a -> { throw getTestIAException(); }).throwThrown());
        assertThrows(IOException.class, () -> Value.of(123).map(a -> { throw new IOException(); }).throwThrown());
        assertDoesNotThrow(() -> Value.of(123).map(a -> a + 10).throwThrown());
    }
    @Test void throwThrownUnchecked() {
        assertThrows(IllegalArgumentException.class, () -> Value.of(123).map(a -> { throw getTestIAException(); }).throwThrownUnchecked());
        assertThrows(WrappedException.class, () -> Value.of(123).map(a -> { throw new IOException(); }).throwThrownUnchecked());
        assertDoesNotThrow(() -> Value.of(123).map(a -> a + 10).throwThrownUnchecked());
    }
    @Test void throwThrownType() {
        assertThrows(IllegalArgumentException.class, () -> Value.of(123).map(a -> { throw getTestIAException(); }).throwThrown(IllegalArgumentException.class));
        assertDoesNotThrow(() -> Value.of(123).map(a -> { throw getTestIAException(); }).throwThrown(IllegalStateException.class));
        assertDoesNotThrow(() -> Value.of(123).map(a -> a + 10).throwThrown(IllegalStateException.class));
    }
    @Test void not() {
        assertThat(Value.of(1).not(n -> false).isPresent(), is(true));
        assertThat(Value.of(1).not(n -> true).isPresent(), is(false));
        assertThat(Value.empty().not(n -> { notSupposedToGetHere(); return false; }).isPresent(), is(false));
    }
    @Test void notNullable() {
        assertThat(Value.of(1)           .notNullable().isPresent(), is(true));
        assertThat(Value.of(null)                      .isPresent(), is(false));
        assertThat(Value.ofNullable(null)              .isPresent(), is(true));
        assertThat(Value.ofNullable(null).notNullable().isPresent(), is(false));
        assertThat(Value.ofNullable(null).notNullable().orSupply(() -> null).isPresent(), is(false));
    }
    @Test void nullable() {
        assertThat(Value.of(null)                   .isPresent(), is(false));
        assertThat(Value.of(null)        .nullable().isPresent(), is(false));
        assertThat(Value.of(null)        .nullable().orSupply(() -> null).orElse(""), is(nullValue()));
        assertThat(Value.ofNullable(null)           .orSupply(() -> null).orElse(""), is(nullValue()));
        assertThat(Value.ofNullable(null).nullable().orSupply(() -> null).orElse(""), is(nullValue()));
        assertThat(Value.of("not null"  ).nullable().orSupply(() -> null).orElse(""), is("not null"));
    }
    @Test void stream() {
        assertThat(Value.of("a").stream().count(), is(1L));
        assertThat(Value.of("a").stream().findFirst().orElse(""), is("a"));
        assertThat(Value.empty().stream().count(), is(0L));
    }
    @Test void vstream() {
        assertThat(Value.of("a").vstream().count(), is(1L));
        assertThat(Value.of("a").vstream().findFirst().orElse(""), is("a"));
        assertThat(Value.empty().vstream().count(), is(0L));
    }

    @Test void or() {
        assertThat(Value.absent().or(2), is(Value.of(2)));
        assertThat(Value.of(1).or(2), is(Value.of(1)));
        assertThat(Value.absent().or((Object)null), is(Value.absent()));
        assertThat(Value.absent().nullable().or((Object)null), is(Value.ofNull()));
    }
    @Test void orValue() {
        assertThat(Value.empty().or(Value.of(2)), is(Value.of(2)));
        assertThat(Value.of(1).or(Value.of(2)), is(Value.of(1)));
    }
    @Test void orSupply() {
        assertThat(Value.of(123).orSupply(() -> { notSupposedToGetHere(); return null; }).isPresent(), is(true));
        assertThat(Value.empty().orSupply(() -> null).isPresent(), is(false));
        assertThat(Value.empty().orSupply(() -> 123), is(Value.of(123)));
        assertDoesNotThrow(() -> Value.empty().orSupply(() -> { throw getTestIAException(); }));
        assertThat(Value.empty().orSupply(() -> { throw getTestIAException(); }).isPresent(), is(false));
        assertThat(Value.empty().orSupply(() -> { throw getTestIAException(); }).isThrown(), is(true));
        assertThat(Value.empty().notNullable().orSupply(() -> null).isPresent(), is(false));
        assertThat(Value.empty().   nullable().orSupply(() -> null).isPresent(), is(true));
    }
    @Test void orSupplyOrThrow() {
        assertThat(Value.of(123).orSupplyOrThrow(() -> { notSupposedToGetHere(); return null; }).isPresent(), is(true));
        assertThat(Value.empty().orSupplyOrThrow(() -> null).isPresent(), is(false));
        assertThat(Value.empty().orSupplyOrThrow(() -> 123), is(Value.of(123)));
        assertThrows(RuntimeException.class, () -> { throw getTestIAException(); });
    }
    @Test void orSupplyValue() {
        assertThat(Value.of(123).orSupplyValue(() -> { notSupposedToGetHere(); return null; }).isPresent(), is(true));
        assertThat(Value.empty().orSupplyValue(() -> null).isPresent(), is(false));
        assertThat(Value.empty().orSupplyValue(() -> Value.of(123)), is(Value.of(123)));
        assertDoesNotThrow(() -> Value.empty().orSupplyValue(() -> { throw getTestIAException(); }));
        assertThat(Value.empty().orSupplyValue(() -> { throw getTestIAException(); }).isPresent(), is(false));
        assertThat(Value.empty().orSupplyValue(() -> { throw getTestIAException(); }).isThrown(), is(true));
    }
    @Test void orSupplyValueOrThrow() {
        assertThat(Value.of(123).orSupplyValueOrThrow(() -> { notSupposedToGetHere(); return null; }).isPresent(), is(true));
        assertThat(Value.empty().orSupplyValueOrThrow(() -> null).isPresent(), is(false));
        assertThat(Value.empty().orSupplyValueOrThrow(() -> Value.of(123)), is(Value.of(123)));
        assertThrows(RuntimeException.class, () -> Value.empty().orSupplyValueOrThrow(() -> { throw getTestIAException(); }));
    }
    @Test void orElse() {
        assertThat(Value.of(1).orElse(2), is(1));
        assertThat(Value.empty().orElse(2), is(2));
    }
    @Test void orElseGet() {
        assertThat(Value.of(1).orElseGet(() -> 2), is(1));
        assertThat(Value.empty().orElseGet(() -> 2), is(2));
    }
    @Test void orElseThrow() {
        assertDoesNotThrow(() -> Value.of(1).orElseThrow());
        assertThat(Value.of(1).orElseThrow(), is(1));
        assertThrows(NoSuchElementException.class, () -> Value.empty().orElseThrow());
    }
    @Test void orElseThrowFromSupplier() {
        assertDoesNotThrow(() -> Value.of(1).orElseThrow(IllegalArgumentException::new));
        assertThat(Value.of(1).orElseThrow(IllegalArgumentException::new), is(1));
        assertThrows(IllegalArgumentException.class, () -> Value.empty().orElseThrow(IllegalArgumentException::new));
    }
    @Test void empty() {
        assertThat(Value.empty().isPresent(), is(false));
    }
    @Test void absent() {
        assertThat(Value.absent().isPresent(), is(false));
        assertThat(Value.absent().isAbsent(), is(true));
    }

    @Test void ofNotNullable() {
        // of() is alias of ofNotNullable()
        assertThat(Value.of(1).isPresent(), is(true));
        assertThat(Value.of(1).get(), is(1));
        assertThat(Value.of(null).isPresent(), is(false));
        assertThat(Value.of(null).map(v -> 1).isPresent(), is(false));
        assertThat(Value.of(1).map(v -> null).isPresent(), is(false));
        assertThat(Value.of(1).map(v -> 2).get(), is(2));
        assertThat(Value.of(null).isNullable(), is(false));
        assertThat(Value.of("ab").isNullable(), is(false));
    }
    @Test void ofNullable() {
        assertThat(Value.ofNullable(null).isPresent(), is(true));
        assertThat(Value.ofNullable(null).get(), is(nullValue()));
        assertThat(Value.ofNullable(null).orElse("abc"), is(nullValue()));
        assertThat(Value.ofNullable(1).isPresent(), is(true));
        assertThat(Value.ofNullable(null).isNullable(), is(true));
        assertThat(Value.ofNullable("ab").isNullable(), is(true));
    }
    @Test void ofNull() {
        assertThat(Value.ofNull().isPresent(), is(true));
        assertThat(Value.ofNull().get(), is(nullValue()));
        assertThat(Value.ofNull().isNullable(), is(true));
    }
    @Test void ofThrown() {
        assertThat(Value.ofThrown(getTestIAException()).isPresent(), is(false));
        assertThat(Value.ofThrown(getTestIAException()).isThrown(), is(true));
    }
    @Test void ofOptional() {
        assertThat(Value.ofOptional(Optional.empty()), is(Value.empty()));
        assertThat(Value.ofOptional(Optional.of(1)), is(Value.of(1)));
    }

    @Test void supply() {
        assertThat(Value.supply(() -> 1).orElse(2), is(1));
        assertThat(Value.supply(() -> null).isAbsent(), is(true));
        assertThat(Value.supply(() -> { throw getTestIAException(); }).isThrown(), is(true));
    }
    @Test void supplyNullable() {
        assertThat(Value.supplyNullable(() -> 1).orElse(2), is(1));
        assertThat(Value.supplyNullable(() -> null).isAbsent(), is(false));
        assertThat(Value.supplyNullable(() -> { throw getTestIAException(); }).isThrown(), is(true));
    }
    @Test void supplyValue() {
        assertThat(Value.supplyValue(() -> null).isAbsent(), is(true));
        assertThat(Value.supplyValue(Value::absent).isAbsent(), is(true));
        assertThat(Value.supplyValue(() -> Value.of(123)).orElse(0), is(123));
        assertThat(Value.supplyValue(() -> { throw getTestIAException(); }).isThrown(), is(true));
        assertThrows(Error.class, () -> Value.supplyValue(() -> { throw new Error(); }));
    }

    @Test void supplyOptional() {
        //noinspection ConstantConditions -- Optional.of(null) is supposed to throw npe
        assertThat(Value.supplyOptional(() -> Optional.of(null)), is(Value.of(null)));
        //noinspection ConstantConditions -- Optional.of(null) is supposed to throw npe
        assertThat(Value.supplyOptional(() -> Optional.of(null)).isThrown(), is(false));
        assertThat(Value.supplyOptional(() -> { throw new NullPointerException(); }).isThrown(), is(true));
        assertThat(Value.supplyOptional(() -> Optional.of(123)).orElse(0), is(123));
    }
    @Test void supplyOptionalAsNullable() {
        //noinspection ConstantConditions -- Optional.of(null) is supposed to throw npe
        assertThat(Value.supplyOptionalAsNullable(() -> Optional.of(null)), is(Value.ofNullable(null)));
        //noinspection ConstantConditions -- Optional.of(null) is supposed to throw npe
        assertThat(Value.supplyOptionalAsNullable(() -> Optional.of(null)).isThrown(), is(false));
        assertThat(Value.supplyOptionalAsNullable(() -> { throw new NullPointerException(); }).isThrown(), is(true));
        assertThat(Value.supplyOptionalAsNullable(() -> Optional.of(123)).orElse(0), is(123));
    }

    @Test void notNullValue() {
        assertThat(Value.ofNotNullable(1), is(Value.of(1)));
        assertThat(Value.ofNotNullable(null), is(Value.empty()));
    }
    @Test void orNullables() {
        assertThat(Value.or(), is(Value.empty()));
        assertThat(Value.or(null, null, null), is(Value.empty()));
        assertThrows(NullPointerException.class, () -> Value.or((String)null, null, null));
        assertThat(Value.or("abc", null, null), is("abc"));
        assertThat(Value.or(null, "abc", null), is("abc"));
        assertThat(Value.or(null, "abc", "def"), is("abc"));
    }
    @Test void orValues() {
        assertThat(Value.or(Value.of("abc"), null), is(Value.of("abc")));
        assertThat(Value.or(Value.empty(), null, Value.of("abc")), is(Value.of("abc")));
        assertThat(Value.or(Value.empty(), Value.empty()), is(Value.empty()));
        assertThat(Value.or(Value.empty(), null), is(Value.empty()));
    }
    @Test void testEquals() {
        assertThat(Value.of(1).equals(Value.of(1)), is(true));
        assertThat(Value.of(1).equals(Value.of(2)), is(false));
        assertThat(Value.of(1).equals(Value.empty()), is(false));
    }
}