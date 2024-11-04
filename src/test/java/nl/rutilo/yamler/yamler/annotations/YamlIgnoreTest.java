package nl.rutilo.yamler.yamler.annotations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import nl.rutilo.yamler.yamler.Yamler;
import org.junit.jupiter.api.Test;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;

class YamlIgnoreTest {

    //<editor-fold desc="Test @YamlIgnore on class">
    @Builder
    @EqualsAndHashCode
    @ToString
    @YamlIgnore({"stringValue"})
    public static class TestIgnoreFieldsOnClass {
        public final int intValue;
        public final String stringValue;
        public final boolean boolValue;
    }
    @Test
    void testIgnoreFieldsOnClass() {
        final TestIgnoreFieldsOnClass obj = TestIgnoreFieldsOnClass.builder()
            .intValue(1)
            .boolValue(true)
            .build();
        final String yaml = "{\"intValue\":1,\"stringValue\":\"text\",\"boolValue\":true}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestIgnoreFieldsOnClass.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnore on method">
    @EqualsAndHashCode
    @ToString
    public static class TestIgnoreOnGettersSetters {
        private int intValue;
        private String stringValue;
        private boolean boolValue;

        public TestIgnoreOnGettersSetters setIntValue(int val) { intValue = val; return this; }
        public int getIntValue() { return intValue; }
        @YamlIgnore
        public  TestIgnoreOnGettersSetters setStringValue(String val) { stringValue = val; return this; }
        public String getStringValue() { return stringValue; }
        public  TestIgnoreOnGettersSetters setBoolValue(boolean val) { boolValue = val; return this; }
        @YamlIgnore
        public boolean getBoolValue() { return boolValue; }
    }
    @Test void TestIgnoreOnGettersSetters() {
        final TestIgnoreOnGettersSetters obj = new TestIgnoreOnGettersSetters().setIntValue(1).setStringValue("text").setBoolValue(true);
        assertThat(Yamler.toJsonString(obj), is("{\"intValue\":1}"));
        final String jsonLc = "{`intValue`:1,`stringValue`:`text`,`boolValue`:true}";
        assertThat(new Yamler().mapYamlToClass(jsonLc, TestIgnoreOnGettersSetters.class), is(new TestIgnoreOnGettersSetters().setIntValue(1)));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnore on field">
    @Builder
    @EqualsAndHashCode
    @ToString
    public static class TestIgnoreFields {
        public final int intValue;
        @YamlIgnore
        public final String stringValue;
        public final boolean boolValue;
    }
    @Test void testIgnoreField() {
        final TestIgnoreFields obj = TestIgnoreFields.builder()
            .intValue(1)
            .boolValue(true)
            .build();
        final String yaml = "{`intValue`:1,`stringValue`:`text`,`boolValue`:true}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestIgnoreFields.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnore on constructor parameter">
    @EqualsAndHashCode
    @ToString
    public static class TestIgnoreOnConstructorParams {
        public int intValue;
        public String stringValue;
        public boolean boolValue;

        public TestIgnoreOnConstructorParams(int intValue, @YamlIgnore String stringValue, boolean boolValue) {
            this.intValue = intValue; this.stringValue = stringValue; this.boolValue = boolValue;
        }
    }
    @Test void testIgnoreOnConstructorParams() {
        final TestIgnoreOnConstructorParams obj = new TestIgnoreOnConstructorParams(1, "text", true);
        assertThat(Yamler.toJsonString(obj), is("{\"intValue\":1,\"boolValue\":true}"));
        final String yaml = "{`intValue`:1,`stringValue`:`text`,`boolValue`:true}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestIgnoreOnConstructorParams.class), is(new TestIgnoreOnConstructorParams(1,null,true)));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnore on field with all-args constructor">
    //@AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class TestIgnoreOnFieldWithConstructor {
        public int intValue;
        @YamlIgnore
        public String stringValue;
        public boolean boolValue;

        public TestIgnoreOnFieldWithConstructor(int intValue, String stringValue, boolean boolValue) {
            this.intValue = intValue; this.stringValue = stringValue; this.boolValue = boolValue; }
    }
    @Test void testIgnoreOnFieldWithConstructor() {
        final TestIgnoreOnFieldWithConstructor obj = new TestIgnoreOnFieldWithConstructor(1, "text", true);
        assertThat(Yamler.toJsonString(obj), is("{\"intValue\":1,\"boolValue\":true}"));
        final String yaml = "{`intValue`:1,`stringValue`:`text`,`boolValue`:true}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestIgnoreOnFieldWithConstructor.class), is(new TestIgnoreOnFieldWithConstructor(1,null,true)));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnore on all fields">
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class TestIgnoreOnAllFields {
        @YamlIgnore
        public int intValue;
        @YamlIgnore
        public String stringValue;
        @YamlIgnore
        public boolean boolValue;
    }
    @Test void testIgnoreOnAllFields() {
        final TestIgnoreOnAllFields obj = new TestIgnoreOnAllFields(1, "text", true);
        assertThat(Yamler.toJsonString(obj), is("{}"));
        final String yaml = "{`intValue`:1,`stringValue`:`text`,`boolValue`:true}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestIgnoreOnAllFields.class), is(new TestIgnoreOnAllFields(0,null,false)));
    }
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @YamlIgnore
    public static class TestIgnoreOnAllFields2 {
        public int intValue;
        public String stringValue;
        public boolean boolValue;
    }
    @Test void testIgnoreOnAllFields2() {
        final TestIgnoreOnAllFields2 obj = new TestIgnoreOnAllFields2(1, "text", true);
        assertThat(Yamler.toJsonString(obj), is("{}"));
        final String yaml = "{`intValue`:1,`stringValue`:`text`,`boolValue`:true}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestIgnoreOnAllFields2.class), is(new TestIgnoreOnAllFields2(0,null,false)));
    }
    //</editor-fold>
}
