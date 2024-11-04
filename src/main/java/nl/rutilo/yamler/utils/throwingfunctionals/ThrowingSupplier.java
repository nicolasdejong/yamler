package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T> extends Supplier<T> {

    @Override
    default T get() {
        try {
            return getThrows();
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    T getThrows() throws Exception; // NOSONAR -- utility
}
