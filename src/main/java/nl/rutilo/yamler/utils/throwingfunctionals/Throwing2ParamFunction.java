package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Alias for ThrowingBiFunction (which relates to BiFunction) */
@FunctionalInterface
public interface Throwing2ParamFunction<T,U,R> extends BiFunction<T,U,R> {

    default R apply(T t, U u) {
        try {
            return applyThrows(t, u);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    R applyThrows(T t, U u) throws Exception; // NOSONAR -- utility

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of output of the {@code after} function, and of the
     *           composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     */
    @Override
    default <V> Throwing2ParamFunction<T, U, V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t, U u) -> after.apply(apply(t, u));
    }
}
