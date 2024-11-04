package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface Throwing4ParamFunction<T,U,V,W,R> {

    default R apply(T t, U u, V v, W w) {
        try {
            return applyThrows(t, u, v, w);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    R applyThrows(T t, U u, V v, W w) throws Exception; // NOSONAR -- utility

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <Z> the type of output of the {@code after} function, and of the
     *           composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     */
    default <Z> Throwing4ParamFunction<T, U, V, W, Z> andThen(Function<? super R, ? extends Z> after) {
        Objects.requireNonNull(after);
        return (T t, U u, V v, W w) -> after.apply(apply(t, u, v, w));
    }
}
