package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.utils.Value;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static nl.rutilo.yamler.yamler.Yamler.toCollections;

/* TODO: recursion detection */
final class JsonStringGenerator {
    private JsonStringGenerator() {}

    public static String generate(Object obj) {
        return generate(obj, 0);
    }
    public static String generate(Object obj, int indent) {
        return toString(obj, indent);
    }

    private static String toString(Object obj, int indent) { // NOSONAR -- multiple returns adds readability here
        if (obj == null              ) return "null";
        if (obj instanceof Optional  ) return toString(((Optional<?>) obj).orElse(null), indent);
        if (obj instanceof Value     ) return toString(((Value<?>) obj).orElse(null), indent);
        if (obj instanceof String    ) return toString((String) obj);
        if (obj instanceof Number    ) return toString((Number) obj);
        if (obj instanceof Boolean   ) return toString((Boolean) obj);
        if (obj instanceof Collection) return toString((Collection<?>) obj, indent);
        if (obj instanceof Map       ) return toString((Map<?, ?>) obj, indent);
        if (obj.getClass().isArray()) {
            final List<Object> list = new ArrayList<>();
            for (int i = 0; i < Array.getLength(obj); i++) list.add(Array.get(obj, i));
            return toString(list, indent);
        }
        return toString(toCollections(obj), indent);
    }

    private static String toString(String s) {
        final char[] chars = s.toCharArray();
        StringBuilder sb = new StringBuilder().append("\"");
        for(final char c : chars) {
            switch(c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case 0x0:  sb.append("\\0"); break;
                case 0x7:  sb.append("\\a"); break;
                case 0xB:  sb.append("\\v"); break;
                case 0x1B: sb.append("\\e"); break;
                case 0x85: sb.append("\\N"); break;
                case 0xa0: sb.append("\\_"); break;
                case 0x2028: sb.append("\\L"); break;
                case 0x2029: sb.append("\\P"); break;
                default:
                    //if(c > 0xFFFF) sb.append("\\U").append(toHexString(c, 8)); // NOSONAR: String has 16-bit chars
                    //else
                    if(c > 0xFF) sb.append("\\u").append(toHexString(c, 4));
                    else
                    if(c >= 0x7F) sb.append("\\x").append(toHexString(c, 2));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
    private static String toString(Number num) {
        if(num instanceof Double) {
            final double d = (Double) num;
            if(d == Double.POSITIVE_INFINITY) return ".inf";
            if(d == Double.NEGATIVE_INFINITY) return "-.inf";
            if(((Double)num).isNaN()) return "nan";
        }
        return num.toString();
    }
    private static String toString(Boolean b) {
        return b.toString();
    }
    private static String toString(Collection<?> list, int indent) {
        return "[" + space(indent)
            + list.stream().map(obj -> toString(obj, indent)).collect(Collectors.joining("," + space(indent)))
            + space(indent) + "]";
    }
    private static String toString(Map<?,?> map, int indent) {
        final String spaces = indent(nextIndent(indent));
        return "{" + newline(indent) + map.entrySet().stream()
            //.filter(e -> e.getKey() != null)
                      // JSON mandates keys being strings, so the below is technically not correct.
                      // It should be e.getKey().toString() instead. Still for the tests it is
                      // convenient to have real objects instead.
                      // TODO: Create YamlStringGenerator with COMPACT option that does this instead
                      //       and then change the below to e.getKey().toString()
            .map(e -> spaces + toString(e.getKey(), -1) + space(indent) + ":" + space(indent) + toString(e.getValue(), nextIndent(indent)))
            .collect(Collectors.joining("," + newline(indent))) + newline(indent) + indent(indent) + "}";
    }
    private static String toHexString(int val, int width) {
        final String hex = Integer.toString(val, 16).toUpperCase(Locale.US);
        return "00000000".substring(0, width - hex.length()) + hex;
    }

    private static String space       (int indent) { return indent < 0 ? "" : " "; }
    private static String newline     (int indent) { return indent < 0 ? "" : "\n"; }
    private static String indent      (int indent) { return indent < 0 ? "" : " ".repeat(indent); }
    private static int    nextIndent  (int indent) { return indent < 0 ? indent : indent + 2; }
}
