package nl.rutilo.yamler.yamler.annotations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import nl.rutilo.yamler.yamler.Yamler;
import org.junit.jupiter.api.Test;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;

class YamlNameTest {

    //<editor-fold desc="Test @YamlName on method">
    @EqualsAndHashCode
    @ToString
    public static class TestNamedMethod {
        private int intValue;
        private String stringValue;

        @YamlName("a")
        public void setIntValue(int val) { this.intValue = val; }
        public int getIntValue() { return intValue; }

        public void setStringValue(String s) { this.stringValue = s; }
        @YamlName("b")
        public String getStringValue() { return stringValue; }
    }
    @Test
    void testNamedMethod() {
        final TestNamedMethod obj = new TestNamedMethod();
        obj.setIntValue(1);
        obj.setStringValue("text");
        final String yaml = "{`a`:1,`b`:`text`,`stringValue`:`should be ignored`}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestNamedMethod.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlName on field">
    @Builder
    @EqualsAndHashCode
    @ToString
    public static class TestNamedField {
        @YamlName("a")
        public final int intValue;
        @YamlName("b")
        public final String stringValue;
    }
    @Test void testNamedField() {
        final TestNamedField obj = TestNamedField.builder()
            .intValue(1)
            .stringValue("text")
            .build();
        final String yaml = "{`a`:1,`b`:`text`,`stringValue`:`should be ignored`}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestNamedField.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlName on constructor parameter">
    @EqualsAndHashCode
    @ToString
    public static class TestNamedParameter {
        public final int intValue;
        public final String stringValue;

        public TestNamedParameter(@YamlName("a") int intValue, @YamlName("b") String stringValue) {
            this.intValue = intValue;
            this.stringValue = stringValue;
        }
    }
    @Test void testNamedParameter() {
        final TestNamedParameter obj = new TestNamedParameter(1, "text");
        final String yaml = "{`a`:1,`b`:`text`,`stringValue`:`should be ignored`}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestNamedParameter.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlName on setter method parameter">
    @EqualsAndHashCode
    @ToString
    public static class TestNamedMethodParameter {
        private int intValue;
        private String stringValue;

        public void setIntValue(@YamlName("a") int val) { this.intValue = val; }
        public int getIntValue() { return intValue; }

        public void setStringValue(@YamlName("b") String s) { this.stringValue = s; }
        public String getStringValue() { return stringValue; }
    }
    @Test void testNamedMethodParameter() {
        final TestNamedMethodParameter obj = new TestNamedMethodParameter();
        obj.setIntValue(1);
        obj.setStringValue("text");
        final String yaml = "{`a`:1,`b`:`text`,`stringValue`:`should be ignored`}";
        assertThat(new Yamler().mapYamlToClass(yaml, TestNamedMethodParameter.class), is(obj));
    }
    //</editor-fold>
}
