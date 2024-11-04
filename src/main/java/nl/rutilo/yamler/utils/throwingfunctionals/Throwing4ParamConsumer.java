package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.Objects;

@FunctionalInterface
public interface Throwing4ParamConsumer<T,U,V,W> {

    default void accept(T t, U u, V v, W w) {
        try {
            acceptThrows(t, u, v, w);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    void acceptThrows(T t, U u, V v, W w) throws Exception; // NOSONAR -- utility

    default Throwing4ParamConsumer<T, U, V, W> andThen(Throwing4ParamConsumer<? super T, ? super U, ? super V, ? super W> after) {
        Objects.requireNonNull(after);

        return (t, u, v, w) -> {
            accept(t, u, v, w);
            after.accept(t, u, v, w);
        };
    }
}
