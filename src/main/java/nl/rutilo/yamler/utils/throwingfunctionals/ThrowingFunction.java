package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.function.Function;

/** Copy of Function that allows checked exceptions to be thrown */
@FunctionalInterface
public interface ThrowingFunction<T,R> extends Function<T,R> {

    @Override
    default R apply(T t) {
        try {
            return applyThrows(t);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    R applyThrows(T t) throws Exception; // NOSONAR -- utility
}
