package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.collections.StringKeyMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;

class YamlTokenizerTest extends BaseYamlTest {

    @Test void testIndent() {
        final List<YamlTokenizer.Token> tokens = new YamlTokenizer(
              "- a\n"
            + "- b\n"
            + "- - cc\n"
            + "  - dd\n"
            + "- e\n"
        ).tokenize();
        final String indents = tokens.stream()
            .filter(t->t.type == YamlTokenizer.TokenType.LIST_ITEM)
            .map(t->t.posInLine)
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        final String expected = "0,0,0,2,2,0";
        assertThat(indents, is(expected));
    }
    @Test void testIndent2() {
        final List<YamlTokenizer.Token> tokens = new YamlTokenizer(
            "- entry\n" +
            "- !!seq\n" +
            " - nested\n" +
            "mapping: !!map\n"
        ).tokenize();
        assertThat(tokens.get(0).type, is(YamlTokenizer.TokenType.LIST_ITEM));
        assertThat(tokens.get(2).type, is(YamlTokenizer.TokenType.LIST_ITEM));
        assertThat(tokens.get(4).type, is(YamlTokenizer.TokenType.LIST_ITEM));

        assertThat(tokens.get(0).posInLine, is(0));
        assertThat(tokens.get(2).posInLine, is(0));
        assertThat(tokens.get(4).posInLine, is(1));
    }

    @Test void testVariablesNone() {
        final String yaml =
            "- ${var1}\n"
          + "- abc ${var2} def\n"
          + "- [ ${var3}, ${var4}, \\${var5} ]\n";
        final List<YamlTokenizer.Token> tokens1 = new YamlTokenizer(YamlerConfig.DEFAULT, yaml).tokenize();
        final String expected1 = "LIST_ITEM,SCALAR:${var1},LIST_ITEM,SCALAR:abc ${var2} def,LIST_ITEM,LIST_START,SCALAR:${var3},SEPARATOR,SCALAR:${var4},SEPARATOR,SCALAR:\\${var5},LIST_END,END";
        assertThat(tokensToString(tokens1), is(expected1));
    }
    @Test void testVariablesDefault() {
        final StringKeyMap variables = new StringKeyMap(Map.of(
          "var1", "val1",
          "var2", "val2",
          "var3", "val3"
        ));
        final String yaml =
            "- ${var1}\n"
          + "- abc ${var2} def\n"
          + "- [ ${var3}, ${var4}, \\${var5} ]\n";
        final List<YamlTokenizer.Token> tokens = new YamlTokenizer(YamlerConfig.builder()
          .variableSyntax("${var}")
          .variables(variables)
          .build(), yaml).tokenize();
        final String expected = "LIST_ITEM,SCALAR:val1,LIST_ITEM,SCALAR:abc val2 def,LIST_ITEM,LIST_START,SCALAR:val3,SEPARATOR,SCALAR:?,SEPARATOR,SCALAR:${var5},LIST_END,END";
        assertThat(tokensToString(tokens), is(expected));
    }
    @Test void testVariablesUsingGetter() {
        final String yaml =
            "- ${var1}\n"
          + "- abc ${var2} def\n"
          + "- [ ${var3}, ${var4}, \\${var5} ]\n";
        final List<YamlTokenizer.Token> tokens = new YamlTokenizer(YamlerConfig.builder()
          .variableSyntax("${var}")
          .variableGetter(varName -> varName.replace("r","l"))
          .build(), yaml).tokenize();
        final String expected = "LIST_ITEM,SCALAR:val1,LIST_ITEM,SCALAR:abc val2 def,LIST_ITEM,LIST_START,SCALAR:val3,SEPARATOR,SCALAR:val4,SEPARATOR,SCALAR:${var5},LIST_END,END";
        assertThat(tokensToString(tokens), is(expected));
    }
    @Test void testVariablesWithDifferentSyntax() {
        final String yaml =
            "- $var1\n"
          + "- abc $var2 def\n"
          + "- [ $var3, $var4, \\$var5 ]\n";
        final List<YamlTokenizer.Token> tokens = new YamlTokenizer(YamlerConfig.builder()
          .variableSyntax("$var")
          .variableGetter(varName -> varName.replace("r","l"))
          .build(), yaml).tokenize();
        final String expected = "LIST_ITEM,SCALAR:val1,LIST_ITEM,SCALAR:abc val2 def,LIST_ITEM,LIST_START,SCALAR:val3,SEPARATOR,SCALAR:val4,SEPARATOR,SCALAR:$var5,LIST_END,END";
        assertThat(tokensToString(tokens), is(expected));
    }
    @Test void testVariablesWithDifferentSyntaxThatNeedsRegexEscape() {
        final String yaml =
            "- .var1.\n"
          + "- abc .var2. def\n"
          + "- [ .var3., .var4., \\.var5. ]\n";
        final List<YamlTokenizer.Token> tokens = new YamlTokenizer(YamlerConfig.builder()
          .variableSyntax(".var.")
          .variableGetter(varName -> varName.replace("r","l"))
          .build(), yaml).tokenize();
        final String expected = "LIST_ITEM,SCALAR:val1,LIST_ITEM,SCALAR:abc val2 def,LIST_ITEM,LIST_START,SCALAR:val3,SEPARATOR,SCALAR:val4,SEPARATOR,SCALAR:.var5.,LIST_END,END";
        assertThat(tokensToString(tokens), is(expected));
    }

    @Test void testYamlFragmentsFromFile() throws IOException {
        testYamlFragmentsFromFile("/test-yaml-fragments-tokens.txt", 't');
    }

    private static String tokensToString(List<YamlTokenizer.Token> tokens) {
        return tokens.stream()
          .map(t -> t.type + (t.value == null ? "" : ":" + t.value))
          .collect(Collectors.joining(","));
    }

}
