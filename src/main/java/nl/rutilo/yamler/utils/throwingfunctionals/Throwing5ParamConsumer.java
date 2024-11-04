package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.Objects;

@FunctionalInterface
public interface Throwing5ParamConsumer<T,U,V,W,X> {

    default void accept(T t, U u, V v, W w, X x) {
        try {
            acceptThrows(t, u, v, w, x);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    void acceptThrows(T t, U u, V v, W w, X x) throws Exception; // NOSONAR -- utility

    default Throwing5ParamConsumer<T, U, V, W, X> andThen(Throwing5ParamConsumer<? super T, ? super U, ? super V, ? super W, ? super X> after) {
        Objects.requireNonNull(after);

        return (t, u, v, w, x) -> {
            accept(t, u, v, w, x);
            after.accept(t, u, v, w, x);
        };
    }
}
