package nl.rutilo.yamler.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Configuration {
    public enum VarDeclDefaultModifier { FINAL, MUTABLE }
    public static class FqnConfigItem {
        public final Pattern                fqnPattern;
        public final VarDeclDefaultModifier fqnDefault;
        public FqnConfigItem(Pattern pat, String defaultModifierText) {
            fqnPattern = pat;
            fqnDefault = "FINAL".equalsIgnoreCase(defaultModifierText) ? VarDeclDefaultModifier.FINAL : VarDeclDefaultModifier.MUTABLE;
        }
        public int scoreForType(String fullyQualifiedTypeName) {
            final Matcher mat = fqnPattern.matcher(fullyQualifiedTypeName);
            if(!mat.find()) return -1;
            final List<String> groups = IntStream.rangeClosed(1, mat.groupCount())
              .mapToObj(mat::group)
              .map(g -> g.replaceAll("(^\\.)|(\\.$)",""))
              .toList();
            if(groups.isEmpty()) return 0;

            return groups.stream().map(g -> g.split("\\.",0).length).reduce(0, Integer::sum)
                 + mat.end(mat.groupCount()) * 100;
        }
    }
    public final List<FqnConfigItem> fqnDefaultModifiers;

    //Configuration(VirtualFile file) {
    //    this(getFileContent(file));
    //}
    Configuration(String configText) {
        fqnDefaultModifiers = defaultModifiersTextFromConfigText(normalizeTextContent(configText))
          .stream()
          .map(Configuration::fromDefaultModifiersText)
          .toList();
    }

    public VarDeclDefaultModifier getDefaultForType(String fullyQualifiedTypeName) {
        return fqnDefaultModifiers.stream()
          .max(Comparator.comparingInt(a -> a.scoreForType(fullyQualifiedTypeName)))
          .map(pakDefaultConfig -> pakDefaultConfig.fqnDefault)
          .orElse(VarDeclDefaultModifier.FINAL);
    }

    //private static String getFileContent(VirtualFile file) {
    //    try {
    //        return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
    //    } catch (IOException e) {
    //        return "";
    //    }
    //}
    private static String normalizeTextContent(String textContent) {
        return textContent
          .replaceAll("(\r\n|\r)", "\n")              // normalize newlines
          .replaceAll("(^|(?<=\n))\\s*#[^\n]*\n", "") // remove remarks
          .replaceAll("[\t]", "  ")                   // no tabs
          .replaceAll(" +\n", "\n")                   // rtrim lines
          .replaceAll("\n{2,}", "\n")                 // remove empty lines
          ;
    }
    private static List<String> defaultModifiersTextFromConfigText(String configText) {
        final Pattern pat = Pattern.compile("(^|\n)\\s*defaults:\\s+((?: +[^\n]+\n)+).*");
        final Matcher mat = pat.matcher(configText + "\n");
        if(mat.find()) {
            return List.of(mat.group(2).trim().split(" *\n *"));
        } else {
            return Collections.emptyList();
        }
    }
    private static FqnConfigItem fromDefaultModifiersText(String defaultModifiersText) {
        final String[] parts = defaultModifiersText.split("\\s*:\\s*");
        final String patText = parts[0];
        final String patType = parts.length < 2 ? "final" : parts[1];

        final List<String> betweenStars = Stream.of(patText.split("\\*"))
          .map(Pattern::quote)
          .map(p -> "(" + p + ")")
          .toList();
        return new FqnConfigItem(
          Pattern.compile(String.join(".*", betweenStars) + (patText.endsWith("*") ? ".*" : "")),
          patType
        );
    }
}
