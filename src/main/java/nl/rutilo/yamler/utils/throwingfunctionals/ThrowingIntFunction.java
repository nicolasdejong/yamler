package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.function.IntFunction;

/** Copy of IntFunction that allows checked exceptions to be thrown */
@FunctionalInterface
public interface ThrowingIntFunction<R> extends IntFunction<R> {

    /**
     * Applies this function to the given argument.
     *
     * @param value the function argument
     * @return the function result
     */
    default R apply(int value) {
        try {
            return applyThrows(value);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    R applyThrows(int value) throws Exception; // NOSONAR -- utility
}
