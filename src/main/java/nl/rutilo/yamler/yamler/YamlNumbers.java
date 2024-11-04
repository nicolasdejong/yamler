package nl.rutilo.yamler.yamler;

import lombok.experimental.UtilityClass;
import nl.rutilo.yamler.utils.Value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"squid:S3358", "squid:S3776"})
// S3358: Don't nest conditionals -- Can be more readable if written concise
// S3776: Yes, some code has too high cyclox
@UtilityClass
public class YamlNumbers {
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "^(?:\\+)?(-)?" +
            "(?:0x([0-9a-fA-F]+))?" + // hex
            "(?:(?:0o|0)([1-7][0-7]*))?" + // octal
            "((?:0|[1-9][0-9]*|(?=\\.))(\\.[0-9]+)?([eE][-+][1-9][0-9]*)?)?" + // NOSONAR -- empty string is checked for
            "$");
    private static final int NP_GROUP_MINUS  = 1;
    private static final int NP_GROUP_HEX    = 2;
    private static final int NP_GROUP_OCTAL  = 3;
    private static final int NP_GROUP_NUMBER = 4;
    private static final int NP_GROUP_NUMDOT = 5;

    public static Value<Number> toNumber(String text) {
        // Numbers can be like: 123, -123, 1.234, .123, -.123, -0.12E-3, 0o223, 0x007BABE

        // Quick check to prevent doing a more expensive match
        char c0 = text.isEmpty() ? 0 : text.charAt(0);
        if(c0 == '-' || c0 == '.') c0 = text.length() > 1 ? text.charAt(1) : 0;
        if(c0 == '-' || c0 == '.') c0 = text.length() > 2 ? text.charAt(2) : 0;
        if((c0 < '0' || c0 > '9') && c0 != '+') return Value.empty();

        // Match against pattern and get groups
        final Matcher matcher = NUMBER_PATTERN.matcher(text);
        if(!matcher.matches()) return Value.empty();

        final String[] groups = new String[matcher.groupCount()+1];
        for(int i=0; i<groups.length; i++) groups[i] = matcher.group(i);
        final String minus = Value.of(groups[NP_GROUP_MINUS]).orElse("");

        return
            groups[NP_GROUP_NUMDOT] != null ? parseToDouble   (minus + groups[NP_GROUP_NUMBER]) :
            groups[NP_GROUP_NUMBER] != null ? parseToIntOrLong(minus + groups[NP_GROUP_NUMBER], 10) :
            groups[NP_GROUP_HEX]    != null ? parseToIntOrLong(minus + groups[NP_GROUP_HEX], 16) :
            groups[NP_GROUP_OCTAL]  != null ? parseToIntOrLong(minus + groups[NP_GROUP_OCTAL], 8) :
            Value.empty();
    }
    private static Value<Number> parseToIntOrLong(String s, int radix) {
        try {
            final long num = Long.parseLong(s, radix);
            return Value.of(num > Integer.MIN_VALUE && num < Integer.MAX_VALUE
                ? (Number) (int) num // without (Number) cast this will cast to Long
                : (Number) num
            );
        } catch(final NumberFormatException e) {
            return Value.empty();
        }
    }
    private static Value<Number> parseToDouble(String s) {
        try {
            return Value.of(Double.parseDouble(s));
        } catch(final NumberFormatException e) {
            return Value.empty();
        }
    }
}
