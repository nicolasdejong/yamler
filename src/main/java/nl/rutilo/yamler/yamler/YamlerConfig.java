package nl.rutilo.yamler.yamler;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.RequiredArgsConstructor;
import nl.rutilo.yamler.collections.Collections2;
import nl.rutilo.yamler.utils.Converters;
import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.exceptions.YamlerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Builder(toBuilder = true)
public class YamlerConfig {
    private static final Supplier<Map<Object,Object>> DEFAULT_MAP_GENERATOR  = HashMap::new;
    private static final Supplier<List<Object>>       DEFAULT_LIST_GENERATOR = ArrayList::new;
    private static final Map<String, Function<ParseInfo, Object>> DEFAULT_PARSE_HANDLERS = Map.of(
        "null",  pi -> { pi.nextObject(false); return null; },
        "str",   pi -> Value.of(pi.nextObject(false)).map(String::valueOf).mapEmpty(() -> "").get(),
        "seq",   pi -> Collections2.toCollection(pi.nextObject(true)),
        "map",   pi -> Collections2.toMap(pi.nextObject(true)),
        "int",   pi -> Converters.toInt(pi.nextObject(false)),
        "float", pi -> Converters.toDouble(pi.nextObject(false))
    );
    public static final YamlerConfig DEFAULT = YamlerConfig.builder().build();
    // TODO: Yaml variables
    // TODO: add example/extra tag handlers
    // TODO: tag handlers should get YamlConfig and be able to provide other YamlConfig for next parse

    public interface ParseInfo {
        Object nextObject(boolean deep);
        byte[] nextBytes(int length);
        YamlerException error(String... args);
    }
    @RequiredArgsConstructor
    public static class TagHandler {
        public final String tagName;
        public final Function<ParseInfo, Object> handler;
    }

    @Default public final Supplier<Map<Object,Object>> defaultMapGenerator = DEFAULT_MAP_GENERATOR;
    @Default public final Supplier<List<Object>>       defaultListGenerator = DEFAULT_LIST_GENERATOR;
    @Default public final Map<String, Function<ParseInfo, Object>> userParseHandlers = DEFAULT_PARSE_HANDLERS;
             public final boolean                      disallowColonsInUnquotedKeys;
    @Default public final String                       variableSyntax = "${var}";
             public final Map<String,?>                variables;
             public final UnaryOperator<String>        variableGetter;

    public static class YamlerConfigBuilder {
        public YamlerConfigBuilder orderedMaps() { return defaultMapGenerator(LinkedHashMap::new); }
    }

//    public YamlerConfig() { this(null, null, false); }
//    public YamlerConfig(Supplier<Map<Object,Object>> defaultMapGenerator,
//                        Supplier<List<Object>>       defaultListGenerator,
//                        boolean disallowColonsInUnquotedKeys,
//                        TagHandler... tagHandlers) {
//        this.defaultMapGenerator  = Optional.ofNullable(defaultMapGenerator ).orElse(DEFAULT_MAP_GENERATOR);
//        this.defaultListGenerator = Optional.ofNullable(defaultListGenerator).orElse(DEFAULT_LIST_GENERATOR);
//        this.disallowColonsInUnquotedKeys = disallowColonsInUnquotedKeys;
//        this.userParseHandlers    = new ReadOnlyMap<>(
//            Arrays.stream(tagHandlers).collect(Collectors.toMap(th -> th.tagName, th -> th.handler))
//        );
//    }

//    public YamlerConfig withOrderedMaps() { return withDefaultMapGenerator(LinkedHashMap::new); }
//    public YamlerConfig withTagHandler(TagHandler tagHandler) { return withTagHandlers(tagHandler); }
//    public YamlerConfig withTagHandlers(TagHandler... tagHandlers) {
//        final Map<String, Function<ParseInfo, Object>> newHandlers = userParseHandlers.toMap();
//        Stream.of(tagHandlers).forEach(th -> newHandlers.put(th.tagName, th.handler));
//        return withUserParseHandlers(new ReadOnlyMap<>(newHandlers));
//    }

    Object handleTag(String name, ParseInfo pi) {
        return  Value.of(name).map(userParseHandlers::get)
                     .or(Value.of(name).map(DEFAULT_PARSE_HANDLERS::get))
                     .orElseThrow(() -> pi.error("Unsupported tag:", name))
                     .apply(pi);
    }
}
