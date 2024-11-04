package nl.rutilo.yamler.yamler;

import lombok.RequiredArgsConstructor;
import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.objectmapper.RSObjectMapper;
import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.exceptions.YamlerException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// TODO: default Optional<> support
// TODO: beforeWrite & afterRead hooks
// TODO: ToYamlString -> toShortYamlString
// TODO: @JsonDefault(String value) for fields
// TODO: ${variable} support

// Perhaps later:
//  - @Json(De)Serialize(as=ValueImpl.class)
//  - @ConvertWith(JsonConverter.class)  JsonConverter then combines JsonSerialize & JsonDeserializer
//  - list ReflectionStrategies to print documentation in case unable to (de)serialize

/**<pre>
 * Reflective YAML reader & Json reader/writer. No external dependencies.
 * This was mostly written as a personal educational exercise. It is fully tested.
 *
 * Features:
 * - Convert json text to Java String, Number, Boolean, List, Map objects.
 * - Convert Java String, Number, Boolean, List, Map objects to json.
 * - Convert json/yaml text to Java objects using reflection (using RSObjectMapper).
 * - Convert Java objects to json text using reflection.
 * - Classes with supported Java fields (String, Number, Boolean, List, Map) won't need custom (de)serializers.
 * - Custom (de)serializers can be used for other Java objects.
 * - Returned maps have extra getter support for various types and chained paths (e.g. map.get("path.to.int", 0))
 *
 * Writer is strict JSON, meaning it will generate fully compliant json.
 *
 * There are a few annotations available for classes or fields to (de)serialize:
 * - YamlIgnoreCase   Ignore the case of the field name (on class for all fields, on field, on getter/setter or constructor param)
 * - YamlName         When name should be different (on field, getter/setter or constructor param)
 * - YamlIgnore       When this field should be ignored (on field, getter/setter or constructor param)
 * - CustomConverter  When set, expects a static fromMap(Map) and (static Map toMap(Object) or Map toMap())
 *
 * Classes with the CustomConverter annotation will be automatically (de)serialized by Yamler.
 * Lombok Builder and AllArgsConstructor (needs -params compiler flag) are supported.
 * TODO: support converter on field level, class level, global level
 *
 * Examples:
 * - Yamler.toMap("{`foo`:`bar`}")      generates Map of String to Object
 * - Yamler.toMap(new MyClass())        generates Map of String to Object for all fields in MyClass
 * - Yamler.toJsonString(new MyClass()) generates Json for all fields in MyClass
 * - Yamler.read(json, MyClass.class) generates MyClass instance from data in json text
 * - Yamler.addSerializer(MyClass.class, myObj -> Map.of("field",myObj.value,...))
 * - Yamler.addDeserializer(MyClass.class, map -> new MyClass(map.get("field",0))
 *
 * Notes:
 * - Getting parameters from constructor only works if the -parameters compiler option is given
 * - Classes with a Builder (e.g. Lombok @Builder) when immutable or getters and setters works best
 * - Only *public* fields and methods will be analyzed
 */
@RequiredArgsConstructor
public class Yamler {
    public final YamlerConfig config;

    public Yamler() { this(YamlerConfig.DEFAULT); }

    public static <T> void addSerializer(Class<T> clazz, Function<T,Map<String,Object>> map) {
        Internal.customSerializers.put((Class<Object>)clazz, (Function<Object, Map<String,Object>>) map);
    }
    public static <T> void addDeserializer(Class<T> clazz, Function<StringKeyMap,T> map) {
        Internal.customDeserializers.put((Class<Object>)clazz, (Function<StringKeyMap,Object>) map);
    }

    /* Parse given yaml text into Java objects (Map, List, String, Number, Boolean).
     *
     * @see: YamlParser#parse(String)
     * @see: YamlDocuments
     */
    public YamlDocuments parseYaml(String yamlText) {
        return new YamlParser(config, yamlText).parse();
    }

    public static String toJsonString(Object obj) { return toJsonString(obj, -1); }
    public static String toJsonString(Object obj, int indent) {
        try {
            return JsonStringGenerator.generate(obj, indent);
        } finally {
            Internal.runState.remove();
        }
    }

    public <T> T mapYamlToClass(String yaml, Class<T> clazz) {
        return mapCollectionsToClass(parseYaml(yaml).first(), clazz);
    }
    public static <T> T mapCollectionsToClass(Object data, Class<T> clazz) {
        if(data == null) data = new HashMap<>();
        if(!(data instanceof Map)) data = Map.of("value", data);
        return mapCollectionsToClass(StringKeyMap.from((Map<Object, Object>) data), clazz);
    }
    public static <T> T mapCollectionsToClass(StringKeyMap map, Class<T> clazz) {
        try {
            return Internal.mapCollectionsToClass(map, clazz);
        } finally {
            Internal.runState.remove();
        }
    }

    public static Object toCollections(Object obj) {
        try {
            return Internal.toCollections(obj);
        } finally {
            Internal.runState.remove();
        }
    }

    private static class Internal {
        private static final int MAX_OBJECT_DEPTH = 30;

        private static final Map<Class<Object>, Function<Object,Map<String,Object>>> customSerializers = Collections.synchronizedMap(new HashMap<>());
        private static final Map<Class<Object>, Function<StringKeyMap,Object>> customDeserializers = Collections.synchronizedMap(new HashMap<>());
        private static class RunState { // only to be used by the private methods below
            int toJsonDepth;
            public RunState clear() { toJsonDepth=0; return this; }
        }
        private static final ThreadLocal<RunState> runState = ThreadLocal.withInitial(RunState::new);

        private static <T> T mapCollectionsToClass(StringKeyMap map, Class<T> clazz) {
            return Value.orSupplyValue(
                // Custom serializer for class
                () -> Value.of(clazz)
                    .flatMap(c -> Value.of(customDeserializers.get(c)))
                    .map(ser -> (T)ser.apply(map)),

                // Supported conversions
                () -> Value.of(RSObjectMapper.convert(clazz, map))
            ).orElseThrow();
        }
        private static Object toCollections(Object obj) {
            if(obj == null) return null;
            if(obj instanceof Optional) return toCollections( ((Optional<?>)obj).orElse(null));
            if(obj instanceof Value)    return toCollections( ((Value<?>)obj).orElse(null));

            try {
                if(runState.get().toJsonDepth++ > MAX_OBJECT_DEPTH) {
                    throw new IllegalStateException("Serialization loop for " + obj);
                }
                return Value.orSupplyValue(
                    () -> Value.of(obj).filter(Internal::isJsonObject),
                    () -> Value.of(obj).filter(YamlDocuments.class::isInstance).map(doc -> toCollections(((YamlDocuments) doc).maps())),
                    () -> Value.of(customSerializers.get(obj.getClass())).map(ser -> ser.apply(obj)),
                    () -> Value.of(RSObjectMapper.getStrategy(obj.getClass()).createMapFrom(obj))
                ).orElseThrow(() -> new YamlerException("Don't know how to create json from " + obj));
            } finally {
                runState.get().toJsonDepth--;
            }
        }
        private static boolean isJsonObject(Object obj) {
            return obj == null
                || obj instanceof String
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof Collection
                || obj.getClass().isArray()
                || obj instanceof Map
                ;
        }
    }
}
