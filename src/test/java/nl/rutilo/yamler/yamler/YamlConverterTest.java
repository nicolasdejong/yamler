package nl.rutilo.yamler.yamler;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.annotations.CustomMapper;
import nl.rutilo.yamler.yamler.annotations.YamlIgnoreCase;
import nl.rutilo.yamler.yamler.exceptions.YamlerException;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlConverterTest {
    private static String quoted(String s) { return s.replace("`", "\""); }
    private static <K,V> Map<K,V> mapOf() { return Collections.emptyMap(); }
    private static <K,V> Map<K,V> mapOf(K key0, V val0, Object... keyValues) {
        final Map<K,V> map = new LinkedHashMap<>(); // Map.of() has no guaranteed order
        map.put(key0, val0);
        for(int i=0; i<keyValues.length; i+=2) { //noinspection unchecked
            map.put((K) keyValues[i], (V) keyValues[i + 1]);
        }
        return map;
    }
    private static <K,V> Map<K,V> add(Map<K,V> map, K key, V value) { map.put(key, value); return map; }

    @Builder
    @ToString
    @EqualsAndHashCode
    public static class IntValuesClass {
        public final int a;
        public final int b;
    }

    //<editor-fold desc="Test class with constructor">
    @ToString
    @EqualsAndHashCode
    @RequiredArgsConstructor
    public static class TestClassWithConstructor {
        public final int a;
        public final String s;
        public final double d;
    }
    @Test
    void testConstructor() {
        final TestClassWithConstructor obj = new TestClassWithConstructor(123, "text", 12.5);
        final String json = quoted("{`a`:123,`s`:`text`,`d`:12.5}");
        assertThat(Yamler.toJsonString(obj), is(json));
        assertThat(new Yamler().mapYamlToClass(json, TestClassWithConstructor.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test class with builder & public fields">
    @Builder
    @ToString
    @EqualsAndHashCode
    public static class StringValuesClass {
        public final String a;
        public final String b;
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    public static class TestClassWithBuilderAndPublicFields {
        public final int intValue;
        public final long longValue;
        public final double doubleValue;
        public final boolean booleanValue;
        public final String string;
        public final IntValuesClass ints;
        public final StringValuesClass strings;
    }
    @Test void testBuilder() {
        assertThat(Yamler.toJsonString(IntValuesClass.builder().a(1).b(2).build()), is(quoted("{`a`:1,`b`:2}")));
        assertThat(new Yamler().mapYamlToClass(quoted("{`a`:1,`b`:2}"), IntValuesClass.class), is(IntValuesClass.builder().a(1).b(2).build()));

        final TestClassWithBuilderAndPublicFields obj = TestClassWithBuilderAndPublicFields.builder()
            .intValue(1)
            .longValue(2)
            .doubleValue(3.5)
            .booleanValue(true)
            .string("text")
            .ints(IntValuesClass.builder().a(11).b(22).build())
            .strings(StringValuesClass.builder().a("aa").b("bb").build())
            .build();
        final String json = quoted("{`booleanValue`:true,`doubleValue`:3.5,`intValue`:1,`ints`:{`a`:11,`b`:22},`longValue`:2,`string`:`text`,`strings`:{`a`:`aa`,`b`:`bb`}}");
        assertThat(Yamler.toJsonString(obj), is(json));
        assertThat(new Yamler().mapYamlToClass(json, TestClassWithBuilderAndPublicFields.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test class with builder & getters">
    @Builder
    @Getter
    @ToString
    @EqualsAndHashCode
    public static class TestClassWithBuilderAndGetters {
        private final int intValue;
        private final String string;
    }
    @Test void testBuilderWithGetters() {
        final TestClassWithBuilderAndGetters obj = TestClassWithBuilderAndGetters.builder().intValue(1).string("text").build();
        final String json = Yamler.toJsonString(obj);
        assertThat(Yamler.toJsonString(obj), is(quoted("{`intValue`:1,`string`:`text`}")));
        assertThat(new Yamler().mapYamlToClass(json, TestClassWithBuilderAndGetters.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test class with getters and setters">
    @Data
    @ToString
    @EqualsAndHashCode
    public static class TestClassWithGettersSetters {
        private int intValue;
        private long longValue;
        private double doubleValue;
        private String string;
    }
    @Test void testGettersSetters() {
        final TestClassWithGettersSetters obj = new TestClassWithGettersSetters();
        obj.setIntValue(1);
        obj.setLongValue(2);
        obj.setDoubleValue(3.5);
        obj.setString("text");
        final String json = quoted("{`doubleValue`:3.5,`intValue`:1,`longValue`:2,`string`:`text`}");
        assertThat(Yamler.toJsonString(obj), is(json));
        assertThat(new Yamler().mapYamlToClass(json, TestClassWithGettersSetters.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test class with non-final fields">
    @ToString
    @EqualsAndHashCode
    public static class TestClassWithNonFinalFields {
        public int intValue;
        public long longValue;
        public double doubleValue;
        public String string;
        public List<Integer> intList;
        public long[] longArray;
    }
    @Test void testNonFinalFields() {
        final TestClassWithNonFinalFields obj = new TestClassWithNonFinalFields();
        obj.intValue = 1;
        obj.longValue = 2;
        obj.doubleValue = 3.5;
        obj.string = "text";
        obj.intList = List.of(1, 2, 3);
        obj.longArray = new long[] { 11, 22, 33 };
        final String json = quoted("{`doubleValue`:3.5,`intList`:[1,2,3],`intValue`:1,`longArray`:[11,22,33],`longValue`:2,`string`:`text`}");
        assertThat(Yamler.toJsonString(obj), is(json));
        assertThat(new Yamler().mapYamlToClass(json, TestClassWithNonFinalFields.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test extended class with non-final fields">
    @ToString
    public static class TestClassWithNonFinalFieldsExtended extends TestClassWithNonFinalFields {
        public int more;
    }
    @Test void testNonFinalFieldsExtended() {
        final TestClassWithNonFinalFieldsExtended obj = new TestClassWithNonFinalFieldsExtended();
        obj.intValue = 1;
        obj.longValue = 2;
        obj.doubleValue = 3.5;
        obj.string = "text";
        obj.more = 123;
        obj.intList = List.of(1, 2, 3);
        obj.longArray = new long[] { 11, 22, 33 };
        final String json = quoted("{`doubleValue`:3.5,`intList`:[1,2,3],`intValue`:1,`longArray`:[11,22,33],`longValue`:2,`more`:123,`string`:`text`}");
        assertThat(Yamler.toJsonString(obj), is(json));
        assertThat(new Yamler().mapYamlToClass(json, TestClassWithNonFinalFieldsExtended.class), is(obj));
    }
    //</editor-fold>

    //<editor-fold desc="Test recursive class">
    @Builder
    @ToString
    @EqualsAndHashCode
    public static class TestClassRecursive {
        public final int id;
        public final TestClassRecursive recursive;
    }
    @Test void testRecursiveClass() {
        final TestClassRecursive obj = TestClassRecursive.builder()
            .id(1)
            .recursive(TestClassRecursive.builder()
                .id(2)
                .recursive(TestClassRecursive.builder()
                    .id(3)
                    .build()
                )
                .build()
            )
            .build();
        final String json = quoted("{`id`:1,`recursive`:{`id`:2,`recursive`:{`id`:3}}}");
        assertThat(Yamler.toJsonString(obj), is(json));
        assertThat(new Yamler().mapYamlToClass(json, TestClassRecursive.class), is(obj));

    }
    //</editor-fold>
    //<editor-fold desc="Test extended class with super builder">
    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    public static class TestClassSuper {
        public final int a;
        public final int b;
    }

    @SuperBuilder
    @ToString
    public static class TestClassExtends extends TestClassSuper {
        public final int c;
        public final int d;
    }
    @Test void testExtends() {
        final TestClassExtends obj = TestClassExtends.builder()
            .a(1)
            .b(2)
            .c(3)
            .d(4)
            .build();
        final String json = quoted("{`a`:1,`b`:2,`c`:3,`d`:4}");
        assertThat(Yamler.toJsonString(obj), is(json));
        assertThat(new Yamler().mapYamlToClass(json, TestClassExtends.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test class with collections">
    @Builder
    @ToString
    @EqualsAndHashCode
    public static class TestClassCollections {
        public int[] intArray;
        public String[] stringArray;
        public IntValuesClass[] ivcArray;
        public List<Integer> intList;
        public List<String> stringList;
        public List<IntValuesClass> ivcList;
        public Set<Integer> intSet;
        public Set<String> stringSet;
        public Set<IntValuesClass> ivcSet;
        public Map<String,Integer> intMap;
        public Map<String,String> stringMap;
        public Map<String,IntValuesClass> ivcMap;
    }
    @Test void testCollections() {
        final int[] intArray = new int[] { 1, 2, 3 };
        final String[] stringArray = new String[] { "a", "b", "c" };
        final IntValuesClass[] ivcArray = new IntValuesClass[] { IntValuesClass.builder().a(11).b(22).build(), IntValuesClass.builder().a(33).b(44).build(), IntValuesClass.builder().a(55).b(66).build() };
        final TestClassCollections obj = TestClassCollections.builder()
            .intArray(intArray)
            .intList(List.of(1, 2, 3))
            .intMap(mapOf("key1",1 ,"key2",2 ,"key3",3))
            .intSet(new LinkedHashSet<>(List.of(1, 2, 3)))
            .ivcArray(ivcArray)
            .ivcList(List.of(ivcArray))
            .ivcMap(add(add(add(new LinkedHashMap<>(), "key1",ivcArray[0]),"key2",ivcArray[1]),"key3",ivcArray[2]))
            .ivcSet(new LinkedHashSet<>(List.of(ivcArray)))
            .stringArray(stringArray)
            .stringList(List.of(stringArray))
            .stringMap(add(add(add(new LinkedHashMap<>(), "keyA","a"),"keyB","b"),"keyC","c"))
            .stringSet(new LinkedHashSet<>(List.of(stringArray)))
            .build();
        final String json = quoted(
              "{"
            + "`intArray`:[1,2,3],"
            + "`intList`:[1,2,3],"
            + "`intMap`:{`key1`:1,`key2`:2,`key3`:3},"
            + "`intSet`:[1,2,3],"
            + "`ivcArray`:[{`a`:11,`b`:22},{`a`:33,`b`:44},{`a`:55,`b`:66}],"
            + "`ivcList`:[{`a`:11,`b`:22},{`a`:33,`b`:44},{`a`:55,`b`:66}],"
            + "`ivcMap`:{`key1`:{`a`:11,`b`:22},`key2`:{`a`:33,`b`:44},`key3`:{`a`:55,`b`:66}},"
            + "`ivcSet`:[{`a`:11,`b`:22},{`a`:33,`b`:44},{`a`:55,`b`:66}],"
            + "`stringArray`:[`a`,`b`,`c`],"
            + "`stringList`:[`a`,`b`,`c`],"
            + "`stringMap`:{`keyA`:`a`,`keyB`:`b`,`keyC`:`c`},"
            + "`stringSet`:[`a`,`b`,`c`]"
            + "}"
        );
        assertThat(Yamler.toJsonString(obj), is(json));
        assertThat(new Yamler().mapYamlToClass(json, TestClassCollections.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test custom serialization">
    @Builder
    @EqualsAndHashCode
    @ToString
    public static class CustomClass {
        public Dimension dim;
        public List<Dimension> dims;
    }

    @Test void testGlobalSerializers() {
        Yamler.addSerializer(Dimension.class, dim -> Map.of("width",dim.width,"height",dim.height));
        Yamler.addDeserializer(Dimension.class, map ->
                new Dimension((int)map.getOrDefault("width",0),
                              (int)map.getOrDefault("height",0))
        );
        final CustomClass cc = CustomClass.builder()
            .dim(new Dimension(12,34))
            .dims(List.of(new Dimension(11,22), new Dimension(33,44), new Dimension(55,66)))
            .build();
        final String json = Yamler.toJsonString(cc);
        assertThat(new Yamler().mapYamlToClass(json, CustomClass.class), is(cc));
    }

    @CustomMapper
    @EqualsAndHashCode
    public static class ClassThatNeedsConverter {
        public final Point p;
        public final String s;

        public ClassThatNeedsConverter(String sDifferentName, Point pDifferentName) { this.s = sDifferentName; this.p = pDifferentName; }

        @SuppressWarnings("unused") // used via reflection
        public static ClassThatNeedsConverter fromMap(StringKeyMap map) {
            return new ClassThatNeedsConverter(map.get("sText",""), new Point(map.get("px",0), map.get("py",0)));
        }
        @SuppressWarnings("unused") // used via reflection
        public StringKeyMap toMap() {
            return new StringKeyMap().putc("px", p.x).putc("py", p.y).putc("sText", s);
        }
    }
    @Test void testJsonConverter() {
        final ClassThatNeedsConverter ctn = new ClassThatNeedsConverter("someText", new Point(123,456));
        final String json = Yamler.toJsonString(ctn);
        final ClassThatNeedsConverter ctn2 = new Yamler().mapYamlToClass(json, ClassThatNeedsConverter.class);
        assertThat(ctn2, is(ctn));
    }
    //</editor-fold>
    //<editor-fold desc="Test recursive serialization">
    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class RecA {
        public final int id;
        public final RecB recB;
    }
    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class RecB {
        public final int id;
        public final RecA recA;
    }
    @Test void testRecursiveClass2() {
        final RecA recA = new RecA(1, new RecB(2, new RecA(3, new RecB(4, null))));
        final String json = Yamler.toJsonString(recA);
        final RecA recA2 = new Yamler().mapYamlToClass(json, RecA.class);
        assertThat(recA2, is(recA));
    }
    //</editor-fold>
    //<editor-fold desc="Test (de)serializing optional values">
    @Builder
    @EqualsAndHashCode
    @ToString
    @YamlIgnoreCase
    public static class TestOptionalClass {
        public final int intValue;
        public final Optional<String> stringValue;
        public final Optional<String> noString;
        public final Value<String>    vstringValue;
        public final Value<String>    vnoString;
        public final int noInt;
    }
    @Test void testOptionalField() {
        final TestOptionalClass obj = TestOptionalClass.builder()
            .intValue(1)
            .stringValue(Optional.of("text"))
            .noString(Optional.empty())
            .vstringValue(Value.of("vtext"))
            .vnoString(Value.absent())
            .build();
        final String jsonLc = quoted("{`intvalue`:1,`stringvalue`:`text`,`unusedKey`:`unusedValue`,`vstringvalue`:`vtext`}");
        assertThat(new Yamler().mapYamlToClass(jsonLc, TestOptionalClass.class), is(obj));
    }
    //</editor-fold>

    //<editor-fold desc="Test null values">
    @EqualsAndHashCode
    @ToString
    public static class TestNullsClass {
        public int i;
        public short sh;
        public long l;
        public double d;
        public float f;
        public byte b;
        public boolean bool;
        public Integer iObj;
        public String s;
        public Optional<String> optS;
    }
    @Test void testNullValues() {
        final TestNullsClass empty = new TestNullsClass();
        empty.optS = Optional.empty();
        final TestNullsClass emptyFromJson = new Yamler().mapYamlToClass("{}", TestNullsClass.class);
        assertThat(emptyFromJson, is(empty));
    }
    //</editor-fold>

    //<editor-fold desc="Test error output">
    public static class ClassThatCannotBeSerialized {
        public final Point p;
        public final String s;
        public ClassThatCannotBeSerialized(String sDifferentName, Point pDifferentName) { this.s = sDifferentName; this.p = pDifferentName; }
    }
    @Test void testFailureToSerialize() {
        final ClassThatCannotBeSerialized ctn = new ClassThatCannotBeSerialized("someText", new Point(123,456));
        assertThrows(YamlerException.class, () -> Yamler.toJsonString(ctn));
    }
    //</editor-fold>
}
