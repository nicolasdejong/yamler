package nl.rutilo.yamler.utils.throwingfunctionals;

import nl.rutilo.yamler.utils.WrappedException;

@FunctionalInterface
public interface ThrowingRunnable extends Runnable {

    @Override
    default void run() {
        try {
            runThrows();
        } catch(final Exception thrown) {
            throw WrappedException.toRuntimeException(thrown);
        }
    }

    void runThrows() throws Exception;
}
