package nl.rutilo.yamler.utils;

public class WrappedException extends RuntimeException {
    public final Exception wrapped;

    public WrappedException(Exception wrapped) {
        super("[wrapped] " + wrapped.getClass().getSimpleName() + ": " + wrapped.getMessage());
        this.wrapped = wrapped;
        //noinspection OverridableMethodCallDuringObjectConstruction
        this.setStackTrace(wrapped.getStackTrace());
    }

    public boolean isChecked() {
        return !(wrapped instanceof RuntimeException);
    }

    public static WrappedException wrap(Exception t) {
        return t instanceof WrappedException we ? we : new WrappedException(t);
    }

    public static Exception unwrap(Exception t) {
        return t instanceof WrappedException we ? we.wrapped : t;
    }

    public static RuntimeException toRuntimeException(Exception t) {
        if(t instanceof WrappedException wt) {
            return wt.wrapped instanceof RuntimeException wtw ? wtw : wt;
        }
        return t instanceof RuntimeException re ? re : new WrappedException(t);
    }
}
