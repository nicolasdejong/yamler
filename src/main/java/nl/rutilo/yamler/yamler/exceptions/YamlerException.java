package nl.rutilo.yamler.yamler.exceptions;

public class YamlerException extends RuntimeException {
    public YamlerException(String message) {
        super(message);
    }

    public YamlerException(String message, Exception cause) {
        super(message, cause);
    }
}
