package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.Objects;

@FunctionalInterface
public interface Throwing3ParamConsumer<T,U,V> {

    default void accept(T t, U u, V v) {
        try {
            acceptThrows(t, u, v);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    void acceptThrows(T t, U u, V v) throws Exception; // NOSONAR -- utility

    default Throwing3ParamConsumer<T, U, V> andThen(Throwing3ParamConsumer<? super T, ? super U, ? super V> after) {
        Objects.requireNonNull(after);

        return (t, u, v) -> {
            accept(t, u, v);
            after.accept(t, u, v);
        };
    }
}
