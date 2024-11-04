# Yamler

Reflective YAML reader & Json reader/writer. Created as a personal educational
exercise to experience what it takes to create a YAML parsers that adheres to
the YAML standard. No external dependencies. It is fully tested and compliant
to the json.org spec (see [test data](src/test/resources/test-yaml-fragments.txt)).

Features:
- Convert json text to Java String, Number, Boolean, List, Map objects.
- Convert Java String, Number, Boolean, List, Map objects to json.
- Convert json/yaml text to Java objects using reflection (using [RSObjectMapper](src/main/java/nl/rutilo/yamler/objectmapper/RSObjectMapper.java)).
- Convert Java objects to json text using reflection.
- Classes with supported Java fields (String, Number, Boolean, List, Map) won't need custom (de)serializers.
- Custom (de)serializers can be used for other Java objects.
- Returned maps have extra getter support for various types and chained paths (e.g. map.get("path.to.int", 0))

Writer is strict JSON, meaning it will generate fully compliant json.

There are a few annotations available for classes or fields to (de)serialize:
- `YamlIgnoreCase`   Ignore the case of the field name (on class for all fields, on field, on getter/setter or constructor param)
- `YamlName`         When name should be different (on field, getter/setter or constructor param)
- `YamlIgnore`       When this field should be ignored (on field, getter/setter or constructor param)
- `CustomConverter`  When set, expects a static fromMap(Map) and (static Map toMap(Object) or Map toMap())

Classes with the CustomConverter annotation will be automatically (de)serialized by Yamler.
Lombok Builder and AllArgsConstructor (needs -params compiler flag) are supported.

Examples:
- `Yamler.toMap("{`foo`:`bar`}")`      generates Map of String to Object
- `Yamler.toMap(new MyClass())`        generates Map of String to Object for all fields in MyClass
- `Yamler.toJsonString(new MyClass())` generates Json for all fields in MyClass
- `Yamler.read(json, MyClass.class)` generates MyClass instance from data in json text
- `Yamler.addSerializer(MyClass.class, myObj -> Map.of("field",myObj.value,...))`
- `Yamler.addDeserializer(MyClass.class, map -> new MyClass(map.get("field",0))`

Notes:
- Getting parameters from constructor only works if the -parameters compiler option is given
- Classes with a Builder (e.g. Lombok @Builder) when immutable or getters and setters works best
- Only *public* fields and methods will be analyzed


---

Written in 2021 but only put on Github in 2024.