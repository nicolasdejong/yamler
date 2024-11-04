package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.YamlerConfig.YamlerConfigBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.fail;

abstract class BaseYamlTest {

    static String toPrint(String s) {
        return s == null ? "null" : s.replace("\n","\\n").replace("\r","\\r");
    }
    static <T> T orCatch(Supplier<T> throwingSupplier, Function<Exception, T> catcher) {
        try {
            return throwingSupplier.get();
        } catch(final Exception e) {
            return catcher.apply(e);
        }
    }
    static int diffOffsetOf(String a, String b) {
        final char[] aChars = a.toCharArray();
        final char[] bChars = b.toCharArray();
        final int len = Math.min(aChars.length, bChars.length);
        int offset = 0;
        while(offset<len && aChars[offset] == bChars[offset]) offset++;
        return offset == len && aChars.length == bChars.length ? -1 : offset;
    }
    static String replaceSpecialChars(String s) {
        return s.replace("·", " ")
            .replace("→", "\t")
            .replace("↓\n","\n") // \u2193
            .replace("\u2193\n", "\n")
            .replace("\u21D3\n", "\n")
            .replace("°", "") // used to represent empty scalars
            ;
    }
    static Value<Integer> diffPosOf(String a, String b) {
        final int minLen = Math.min(a.length(), b.length());
        for(int i=0; i<minLen; i++) {
            if(a.charAt(i) != b.charAt(i)) return Value.of(i);
        }
        if(a.length() > minLen || b.length() > minLen) return Value.of(minLen);
        return Value.empty();
    }

    /** Alternative to Map.of() that allows null keys and values */
    public static Map<?,?> mapOf(Object... keysAndValues) {
        final Map<Object,Object> map = new HashMap<>();
        for(int i=0; i<keysAndValues.length; i+=2) map.put(keysAndValues[i], keysAndValues[i+1]);
        return Collections.unmodifiableMap(map);
    }
    /** Alternative to List.of() that allows null values */
    public static List<?> listOf(Object... values) {
        final List<Object> list = new ArrayList<>();
        Collections.addAll(list, values);
        return Collections.unmodifiableList(list);
    }

    void testYamlFragmentsFromFile(String resourceName) throws IOException { testYamlFragmentsFromFile(resourceName, 'y'); }
    void testYamlFragmentsFromFile(String resourceName, char defaultTupleType) throws IOException {
        final String fileText0 = urlToString(YamlerTest.class.getResource(resourceName)).replace("\r\n","\n");
        final int end = fileText0.indexOf(">>== END");
        final String fileText = end < 0 ? fileText0 : fileText0.substring(0, end);
        Arrays
            .stream(fileText.split("@@@@"))
            .skip(1)
            .map(block -> block.split("~~~~"))
            .filter(tuple -> tuple.length >= 2)
            .forEach(tuple -> { // tuples: input(y or empty), expected processed result, tokens(t), configuration (c)
                final String yamlInput = replaceSpecialChars(tuple[0]); // no trim() !
                final Consumer<String> errorHandler = error -> {
                    final int fileLine = fileText.substring(0, fileText.indexOf(String.join("~~~~", tuple))).split("\n").length + 1;
                    fail(error.replaceFirst("\\{line}", ""+fileLine));
                };
                YamlerConfigBuilder configBuilder = YamlerConfig.builder();
                //System.out.println("======================\n" + yamlInput.trim() + "\n======================");

                for(int ti=1; ti<tuple.length; ti++) {
                    if(tuple[ti].length() < 3) continue;
                    char type = tuple[ti].charAt(0);
                    final String text = replaceSpecialChars(tuple[ti].substring(1));

                    if(type == '\n') type = defaultTupleType;

                    switch(type) {
                        case 'c': // configuration
                            if(text.contains("disallowColonsInUnquotedKeys")) configBuilder.disallowColonsInUnquotedKeys(true);
                            break;
                        case 't':
                            testTokens(configBuilder.build(), yamlInput, text, errorHandler);
                            break;
                        case 'y':
                        default:
                            testYaml(configBuilder.build(), yamlInput, text, errorHandler);
                            break;
                    }
                }
            });
    }
    void testTokens(YamlerConfig config, String yamlInput, String expectedResult, Consumer<String> errorHandler) {
        final List<YamlTokenizer.Token> generatedTokens = new YamlTokenizer(config, yamlInput).setSkipRemarks(false).tokenize();
        final List<YamlTokenizer.Token> expectedTokens = Arrays.stream(expectedResult.trim().replaceAll("(?s)\n{2,}.*$","").split("\\s*;\\s*"))
            .map(YamlTokenizer.Token::fromString)
            .collect(Collectors.toList());
        final String genText = generatedTokens.stream().map(YamlTokenizer.Token::toShortString).map(BaseYamlTest::toPrint).collect(Collectors.joining(","));
        final String expText =  expectedTokens.stream().map(YamlTokenizer.Token::toShortString).map(BaseYamlTest::toPrint).collect(Collectors.joining(","));

        diffPosOf(genText, expText).ifPresent(diffPos0 -> {
            final int delta = diffPos0 < 60 ? 0 : (diffPos0 - 60);
            final int diffPos = diffPos0 - delta;
            errorHandler.accept(
                "TOKENS are different around line {line} on char " + diffPos + " of " + expText.length() + ":\n"
                    + "TOKENS GENERATED: " + (delta > 0 ? "..." + genText.substring(delta) : genText) + "\n"
                    + "TOKENS  EXPECTED: " + (delta > 0 ? "..." + expText.substring(delta) : expText) + "\n"
                    + "                  " + (delta > 0 ? "   " : "") + " ".repeat(diffPos) + "^"
            );
        });

    }
    void testYaml(YamlerConfig configIn, String yamlInput, String expectedResult, Consumer<String> errorHandler) {
        final YamlerConfig config         = configIn.toBuilder().orderedMaps().build(); // predictable ordering for diffing
        final Object yamlInputObject      = orCatch(() -> new Yamler(config).parseYaml(yamlInput).value(), e -> mapOf("error", e.getMessage()));
        final Object expectedResultObject = expectedResult.isEmpty() ? null : new Yamler(config).parseYaml(expectedResult).value();
        if(expectedResultObject != null) {
            final String jsonFromYamlData = toJsonString(yamlInputObject);
            final String jsonFromExpcData = toJsonString(expectedResultObject);

            final int diffOffset = diffOffsetOf(jsonFromYamlData, jsonFromExpcData);

            if(diffOffset >= 0) {
                final int sub = diffOffset > 10 && jsonFromYamlData.length() > 80 ? diffOffset - 10 : 0;
                final String dots = sub > 0 ? "..." : "";
                final String os = sub > 0 ? " at offset " + diffOffset : "";
                errorHandler.accept(
                    "YAML to JSON has unexpected result for:\n"
                        + "~~~~~~ YAML input on line {line}:" + yamlInput
                        + "~~~~~~ result JSON" + os + ":\n"  + dots + jsonFromYamlData.substring(sub) + "\n"
                        + "~~~~~~ but expected:\n"           + dots + jsonFromExpcData.substring(sub) + "\n"
                        + (sub > 0 ? "~~~~~~ full result:\n" + jsonFromYamlData : "")
                );
            }
        }
    }
    String toJsonString(Object obj) {
        try {
            return JsonStringGenerator.generate(obj);
        } catch(final Throwable endlessRecursion) {
            // JsonStringGenerator doesn't yet have recursion detection
            return obj == null ? "null" : obj.toString();
        }
    }

    private static String urlToString(URL url) throws IOException {
        try(final InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
