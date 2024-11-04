package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.function.UnaryOperator;

@FunctionalInterface
public interface ThrowingUnaryOperator<T> extends UnaryOperator<T> {

    @Override
    default T apply(T t) {
        try {
            return applyThrows(t);
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    T applyThrows(T t) throws Exception; // NOSONAR -- utility

    static <T> ThrowingUnaryOperator<T> identity() {
        return t -> t;
    }
}
