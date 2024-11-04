package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.Objects;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface ThrowingBiConsumer<T,U> extends BiConsumer<T,U> {

    default void accept(T t, U u) {
        try {
            acceptThrows(t, u);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    void acceptThrows(T t, U u) throws Exception; // NOSONAR -- utility

    @Override
    default ThrowingBiConsumer<T, U> andThen(BiConsumer<? super T, ? super U> after) {
        Objects.requireNonNull(after);

        return (t, u) -> {
            accept(t, u);
            after.accept(t, u);
        };
    }
}
