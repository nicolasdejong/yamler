package nl.rutilo.yamler.utils;

public final class Converters {
    private Converters() {}

    public static int toInt(Object obj) {
        if(obj == null) return 0;
        if(obj instanceof Number number) return number.intValue();
        if(obj instanceof String string) return Integer.parseInt(string);
        throw new IllegalArgumentException("Unable to convert to int: " + obj);
    }
    public static double toDouble(Object obj) {
        if(obj == null) return 0;
        if(obj instanceof Number number) return number.doubleValue();
        if(obj instanceof String string) return Double.parseDouble(string);
        throw new IllegalArgumentException("Unable to convert to double: " + obj);
    }
}
