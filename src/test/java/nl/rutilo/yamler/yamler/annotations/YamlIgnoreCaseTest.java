package nl.rutilo.yamler.yamler.annotations;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import nl.rutilo.yamler.yamler.Yamler;
import org.junit.jupiter.api.Test;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.SilverAssert.assertThat;

class YamlIgnoreCaseTest {

    //<editor-fold desc="Test @YamlIgnoreCase on class">
    @Builder
    @EqualsAndHashCode
    @ToString
    @YamlIgnoreCase
    public static class TestIgnoreCaseClass {
        public final int intValue;
        public final String stringValue;
    }
    @Test void testIgnoreCaseClass() {
        final TestIgnoreCaseClass obj = TestIgnoreCaseClass.builder()
            .intValue(1)
            .stringValue("text")
            .build();
        final String jsonLc = "{`intvalue`:1,`stringvalue`:`text`}";
        assertThat(new Yamler().mapYamlToClass(jsonLc, TestIgnoreCaseClass.class), is(obj));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnoreCase on (getter or setter) method">
    public static class TestIgnoreCaseMethod {
        private int intValue;
        private String stringValue;

        public void setIntValue(int i) { intValue = i; }
        public int getIntValue() { return intValue; }
        @YamlIgnoreCase
        public void setStringValue(String s) { stringValue = s; }
        public String getStringValue() { return stringValue; }
    }
    @Test void testIgnoreCaseMethod() {
        final String jsonLc = "{intvalue: 1,stringvalue: 'text',unused: 123}";
        final TestIgnoreCaseMethod testObj = new Yamler().mapYamlToClass(jsonLc, TestIgnoreCaseMethod.class);
        assertThat(testObj.getIntValue(), is(0));
        assertThat(testObj.getStringValue(), is("text"));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnoreCase on constructor parameter">
    public static class TestIgnoreCaseParameter {
        public final int intValue;
        public final String stringValue;
        public TestIgnoreCaseParameter(final int intValue, @YamlIgnoreCase final String stringValue) {
            this.intValue = intValue;
            this.stringValue = stringValue;
        }
    }
    @Test void testIgnoreCaseParameter() {
        final String jsonLc = "{`intvalue`:1,`stringvalue`:`text`}";
        final TestIgnoreCaseParameter testObj = new Yamler().mapYamlToClass(jsonLc, TestIgnoreCaseParameter.class);
        assertThat(testObj.intValue, is(0));
        assertThat(testObj.stringValue, is("text"));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnoreCase on setter method parameter">
    public static class TestIgnoreCaseMethodParameter {
        private int intValue;
        private String stringValue;

        public void setIntValue(int i) { intValue = i; }
        public int getIntValue() { return intValue; }
        public void setStringValue(@YamlIgnoreCase String s) { stringValue = s; }
        public String getStringValue() { return stringValue; }
    }
    @Test void testIgnoreCaseMethodParameter() {
        final String jsonLc = "{intvalue: 1, stringvalue: 'text', unused: 123}";
        final TestIgnoreCaseMethodParameter testObj = new Yamler().mapYamlToClass(jsonLc, TestIgnoreCaseMethodParameter.class);
        assertThat(testObj.getIntValue(), is(0));
        assertThat(testObj.getStringValue(), is("text"));
    }
    //</editor-fold>
    //<editor-fold desc="Test @YamlIgnoreCase on field">
    @Builder
    public static class TestIgnoreCaseField {
        public final int intValue;
        @YamlIgnoreCase
        public final String stringValue;
    }
    @Test void testIgnoreCaseField() {
        final String jsonLc = "{`intvalue`:1,`stringvalue`:`text`}";
        final TestIgnoreCaseField testObj = new Yamler().mapYamlToClass(jsonLc, TestIgnoreCaseField.class);
        assertThat(testObj.intValue, is(0));
        assertThat(testObj.stringValue, is("text"));
    }
    //</editor-fold>
}
