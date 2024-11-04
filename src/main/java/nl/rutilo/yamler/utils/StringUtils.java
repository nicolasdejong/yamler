package nl.rutilo.yamler.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StringUtils {
    private StringUtils() {}

    public static String replaceMatches(String input, Pattern pattern, Function<Matcher, String> replacer) {
        final Matcher matcher = pattern.matcher(Value.or(input,""));
        final StringBuilder replaced = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(replaced, Value.or(replacer.apply(matcher), ""));
        }
        matcher.appendTail(replaced);
        return replaced.toString();
    }
    public static String replacePattern(String input, Pattern pattern, UnaryOperator<String> replacer) {
        return replaceMatches(input, pattern, mat -> replacer.apply(mat.group()));
    }
    public static String replaceRegex(String input, String regex, Function<String[],String> replacer) {
        return replaceMatches(input, Pattern.compile(regex), mat -> {
            final String[] groups = new String[mat.groupCount() + 1];
            groups[0] = mat.group();
            for(int i=1; i<=mat.groupCount(); i++) groups[i] = mat.group(i);
            return replacer.apply(groups);
        });
    }
    public static String replaceGroups(String input, String regex, String... replacements) {
        final int[] replacementsIndex = { 0 };
        return replaceMatches(input, Pattern.compile(Value.or(regex,"")), mat -> {
            final int offset = mat.start();
            if(mat.groupCount() == 0) {
                return new StringBuilder(mat.group()).replace(0, mat.end() - offset, 0 >= replacements.length ? "" : Value.or(replacements[0], "")).toString();
            }
            final StringBuilder sb = new StringBuilder(mat.group());
            for (int i = mat.groupCount() - 1; i >=0; i--) {
                final int index = replacements.length > 0 ? (replacementsIndex[0] + i) % replacements.length : -1;
                sb.replace(mat.start(i + 1) - offset, mat.end(i + 1) - offset, index < 0 ? "" : Value.or(replacements[index], ""));
            }
            replacementsIndex[0] += mat.groupCount();
            return sb.toString();
        });
    }

    public static Value<String> getRegexMatch(String input, String regex) {
        return getRegexGroup(input, "(" + Value.or(regex,"") + ")");
    }
    public static Value<String> getRegexGroup(String input, String regex) {
        final Matcher matcher = Pattern.compile(Value.or(regex, "")).matcher(Value.or(input, ""));
        if(!matcher.find()) return Value.absent();
        if(matcher.groupCount() < 1) return Value.of(matcher.group());
        return Value.of(matcher.group(1));
    }
    public static List<String> getRegexGroups(String input, String regex) {
        final List<String> results = new ArrayList<>();
        final Matcher matcher = Pattern.compile(Value.or(regex, "")).matcher(Value.or(input, ""));
        while(matcher.find()) {
            if(matcher.groupCount() == 0) results.add(matcher.group());
            for (int i = 0; i < matcher.groupCount(); i++) {
                results.add(matcher.group(i + 1));
            }
        }
        return results;
    }

    public static int countNewlinesIn(String s) {
        if(s == null) return 0;
        boolean skipLF = false;
        int lineCount = 0;
        // Code copied (and slightly improved readability) from LineReader class
        for (final char c : s.toCharArray()) {
            if (skipLF) {
                skipLF = false;
                if (c == '\n') continue;
            }
            if(c == '\r') skipLF = true;
            if(c == '\r' || c == '\n') lineCount++;
        }
        return lineCount;
    }

    public static String shortenPackageName(String pak) {
        if(pak == null || !pak.contains(".")) return pak;
        final String[] parts = pak.split("\\.");
        int lastPakIndex = parts.length-1;
        while(lastPakIndex >= 0 && !parts[lastPakIndex].equals(parts[lastPakIndex].toLowerCase())) lastPakIndex--;
        if(lastPakIndex <= 0) return pak;
        final boolean hasClasses = lastPakIndex < parts.length - 1;
        boolean shortened = false;
        for(int i=0; i<=lastPakIndex; i++) {
            if(parts[i].length() > 1) { parts[i] = parts[i].substring(0,1); shortened = true; break; }
        }
        return shortened || !hasClasses
            ? String.join(".", parts)
            : Stream.of(parts).skip(lastPakIndex + 1L).collect(Collectors.joining("."));
    }
    public static String commonPrefix(String... strings) {
        return commonPrefix(List.of(strings));
    }
    public static String commonPrefix(Collection<String> strings) {
        return strings.stream()
            .reduce((commonPrefix, string) -> {
                String newCP = commonPrefix.substring(0, Math.min(commonPrefix.length(), string.length()));
                while(!newCP.isEmpty() && !string.startsWith(newCP)) {
                    newCP = newCP.substring(0, newCP.length()-1);
                }
                return newCP;
            })
            .orElse("");
    }

    private static String join(List<String> list, String joiner, int first, int count) {
        return list.stream().skip(first).limit(count).collect(Collectors.joining(joiner));
    }

    public static List<String> getPathPrefixesOf(String path) {
        final List<String> prefixes = new ArrayList<>();
        final List<String> parts = List.of(path.split("/"));
        for(int count=1; count<parts.size(); count++) prefixes.add(join(parts, "/", 0, count));
        return prefixes;
    }

    public static String lc(String s) { return s.toLowerCase(Locale.US); }
    public static String lcFirst(String s) {
        return (s.isEmpty() ? "" : s.substring(0,1).toLowerCase(Locale.US))
             + (s.length() > 1 ? s.substring(1) : "");
    }
    public static String ucFirst(String s) {
        return (s.isEmpty() ? "" : s.substring(0,1).toUpperCase(Locale.US))
             + (s.length() > 1 ? s.substring(1) : "");
    }
}
