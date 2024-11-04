package nl.rutilo.yamler.utils;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Map;

/** A tuple is an unmodifiable group of two or more values. Use the of(...) methods to create. */
public final class Tuple {
    private Tuple() {}

    @ToString @EqualsAndHashCode @AllArgsConstructor(access=AccessLevel.PRIVATE)
    public static final class Tuple2<A,B> {
        public final A a;
        public final B b;
    }

    @ToString @EqualsAndHashCode @AllArgsConstructor(access=AccessLevel.PRIVATE)
    public static final class Tuple3<A,B,C> {
        public final A a;
        public final B b;
        public final C c;
    }

    @ToString @EqualsAndHashCode @AllArgsConstructor(access=AccessLevel.PRIVATE)
    public static final class Tuple4<A,B,C,D> {
        public final A a;
        public final B b;
        public final C c;
        public final D d;
    }

    @ToString @EqualsAndHashCode @AllArgsConstructor(access=AccessLevel.PRIVATE)
    public static final class Tuple5<A,B,C,D,E> {
        public final A a;
        public final B b;
        public final C c;
        public final D d;
        public final E e;
    }

    public static <A,B>       Tuple2<A,B>       of(A a, B b)                { return new Tuple2<>(a, b); }
    public static <A,B,C>     Tuple3<A,B,C>     of(A a, B b, C c)           { return new Tuple3<>(a, b, c); }
    public static <A,B,C,D>   Tuple4<A,B,C,D>   of(A a, B b, C c, D d)      { return new Tuple4<>(a, b, c, d); }
    public static <A,B,C,D,E> Tuple5<A,B,C,D,E> of(A a, B b, C c, D d, E e) { return new Tuple5<>(a, b, c, d, e); }

    public static <A,B>       Tuple2<A,B>       of(Map.Entry<A,B> entry) { return of(entry.getKey(), entry.getValue()); }

    @SafeVarargs
    public static <T> Tuple2<T,T>       of2(T... vars) { return new Tuple2<>(vars.length > 0 ? vars[0] : null, vars.length > 1 ? vars[1] : null); }
    @SafeVarargs
    public static <T> Tuple3<T,T,T>     of3(T... vars) { return new Tuple3<>(vars.length > 0 ? vars[0] : null, vars.length > 1 ? vars[1] : null, vars.length > 2 ? vars[2] : null); }
    @SafeVarargs
    public static <T> Tuple4<T,T,T,T>   of4(T... vars) { return new Tuple4<>(vars.length > 0 ? vars[0] : null, vars.length > 1 ? vars[1] : null, vars.length > 2 ? vars[2] : null, vars.length > 3 ? vars[3] : null); }
    @SafeVarargs
    public static <T> Tuple5<T,T,T,T,T> of5(T... vars) { return new Tuple5<>(vars.length > 0 ? vars[0] : null, vars.length > 1 ? vars[1] : null, vars.length > 2 ? vars[2] : null, vars.length > 3 ? vars[3] : null, vars.length > 4 ? vars[4] : null); }
}
