package nl.rutilo.yamler.utils;

import lombok.Builder;
import nl.rutilo.yamler.yamler.annotations.YamlIgnoreCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;

import java.awt.Dimension;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import static nl.rutilo.yamler.testutils.IsMatcher.is;
import static nl.rutilo.yamler.testutils.IsMatcher.isA;
import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

@SuppressWarnings({
    "squid:S5786" // public test class because it is used for public reflection tests
    , "unused" // several methods only exist for doing reflection testing on
})
@DisplayName("ReflectionTest") // Added for testing
@YamlIgnoreCase // Added for testing
public class ReflectionTest { // public because we want to test reflection on this class
    public String testField = "testValue";

    @SuppressWarnings("FieldMayBeFinal") // reflection test
    private String privateField = "private";

    private void privateMethod() {}

    // These methods are unused by only for exist for testing reflection

    public void doThrow() { throw new RuntimeException("debug throw"); }
    public String testMethod() { return "@"; }
    public String testMethod(int a) { if(a == 0) throw new RuntimeException(); return "@" + a; }
    @YamlIgnoreCase
    public String testMethod(int a, String b) { if(a == 0) throw new RuntimeException(); return a + b; }
    public String testMethod(int a, String b, int c) { if(a == 0) throw new RuntimeException(); return a + b + c; }
    public static String staticTestMethod() { return "static"; }

    public @interface SomeInterface {
        int priority() default 5;
    }

    public static class InnerClass {
        public InnerClass() {}
        public InnerClass(int a, String s) { if(a == 0) throw new IllegalArgumentException("a is 0"); }
        private InnerClass(int a) {}
    }
    public static class InnerClassEmpty {}
    @SuppressWarnings("ClassCanBeRecord") // only for testing reflection
    public static class InnerClassWithString {
        final String a;
        public InnerClassWithString(String a) { this.a = a; }
    }

    public static class ThrowingClass {
        public ThrowingClass() { throw new RuntimeException("throws on construction"); }
    }
    public static class PrivateConstructorClass {
        private PrivateConstructorClass() {}
    }
    public static class NoDefaultConstructorClass {
        public NoDefaultConstructorClass(int a) {}
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface TestName {
        String value();
    }

    @TestName("some-test-name")
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface TestInterface {
        String name() default "";
    }

    @TestInterface(name = "name1")
    public String withTestInterface = "abc";


    @BeforeEach public void setup() {
        Reflection.removeAddedAnnotations();
    }

    @Test void getCaller() {
        assertThat(Reflection.getCallerClass(), is(getClass()));
        assertThat(Reflection.getCallerClass(getClass()), is(ReflectionUtils.class));
        assertThat(Reflection.getCallerClass("*.utils.*"), is(ReflectionUtils.class));
    }

    @Test void isJavaClass() {
        assertThat(Reflection.isJavaClass(Reflection.class), is(false));
        assertThat(Reflection.isJavaClass(String.class), is(true));
        assertThat(Reflection.isJavaClass("javax.some.Type"), is(true));
    }

    @Test void getClassByName() {
        assertThat(Reflection.getClass("non.existing.Type").isAbsent(), is(true));
        assertThat(Reflection.getClass("java.lang.String"), is(Value.of(String.class)));
    }
    @Test void getMethodByName() {
        assertThat(Reflection.getMethod(String.class, "nonExisting").isAbsent(), is(true));
        assertThat(Reflection.getMethod(String.class, "substring", Integer.TYPE).isAbsent(), is(false));
    }
    @Test void getMethodDefault() {
        assertThat(Reflection.getMethod(SomeInterface.class, "priority").flatMap(Reflection::getDefaultValueOf).orElse(null), is(5));
        assertThat(Reflection.getDefaultValueOf(null).isPresent(), is(false));
    }
    @Test void getFieldByName() {
        assertThat(Reflection.getField(ReflectionTest.class, "nonExisting").isAbsent(), is(true));
        assertThat(Reflection.getField(ReflectionTest.class, "testField").orElseThrow().getName(), is("testField"));
    }
    @Test void getFieldValue() throws NoSuchFieldException {
        assertThat(Reflection.getFieldValue(ReflectionTest.class, "nonExisting", this), is(Value.empty()));
        assertThat(Reflection.getFieldValue(ReflectionTest.class, "testField", this).orElseThrow(), is("testValue"));
        assertThat(Reflection.getFieldValue(this, "testField").orElseThrow(), is("testValue"));
        assertThat(Reflection.getFieldValue(this, Reflection.getField(ReflectionTest.class, "testField").orElseThrow()).orElseThrow(), is("testValue"));

        final Field privField = ReflectionTest.class.getDeclaredField("privateField");
        assertThat(Reflection.getFieldValue(this, privField).isPresent(), is(false));
    }
    @Test void setFieldValue() throws NoSuchFieldException {
        assertThat(Reflection.setFieldValue(this, "nonExisting", ""), is(false));
        assertThat(Reflection.setFieldValue(this, "testField", "testing123"), is(true));
        assertThat(testField, is("testing123"));
        assertThat(Reflection.setFieldValue(this, "testField", "testValue"), is(true));
        assertThat(testField, is("testValue"));

        assertThat(Reflection.setFieldValue(this, "privateField", "testing123"), is(false));
        assertThat(privateField, is("private"));

        final boolean[] exHandlerCalled = { false };
        final Field privField = ReflectionTest.class.getDeclaredField("privateField");
        assertThat(Reflection.setFieldValue(this, privField, "testValue"), is(false));
        assertThat(Reflection.setFieldValue(this, privField, "testValue", ex -> exHandlerCalled[0] = true), is(false));
        assertThat(exHandlerCalled[0], is(true));
        assertThat(Reflection.setFieldValue(this, "privateField", "testValue", ex -> {}), is(false));
    }
    @Test void getParameterByIndex() {
        assertThat(Reflection
            .getMethod(ReflectionTest.class, "testMethod", Integer.TYPE, String.class)
            .flatMap(m -> Reflection.getParameter(m, 0))
            .map(Parameter::getType)
            .orElseThrow(),
            is(Integer.TYPE)
        );
        assertThat(Reflection
            .getMethod(ReflectionTest.class, "testMethod", Integer.TYPE, String.class)
            .flatMap(m -> Reflection.getParameter(m, 1))
            .map(Parameter::getType)
            .orElseThrow(),
            is(String.class)
        );
        assertThat(Reflection
            .getMethod(ReflectionTest.class, "testMethod", Integer.TYPE, String.class)
            .flatMap(m -> Reflection.getParameter(m, 2)),
            is(Value.empty())
        );
    }
    @Test void getParameterNames() throws NoSuchMethodException { // requires compiler option -parameters
        assertThat(Reflection.getParameterNames(ReflectionTest.class.getMethod("testMethod", Integer.TYPE, String.class)), is(List.of("a", "b")));
    }
    @Test void getParameterTypes() throws NoSuchMethodException {
        assertThat(Reflection.getParameterTypes(ReflectionTest.class.getMethod("testMethod", Integer.TYPE, String.class)), is(List.of(Integer.TYPE, String.class)));
    }

    @Test void hasInterface() {
        assertThat(Reflection.hasInterface(Dimension.class, Serializable.class), is(true));
        assertThat(Reflection.hasInterface(Serializable.class, Dimension.class), is(false));
    }

    @Test void getAnnotationsOn() {
        final List<Annotation> annotations1 = Reflection.getAnnotationsOn(ReflectionTest.class);
        assertThat(annotations1.size(), is(3));
        assertThat(annotations1.get(0), isA(DisplayName.class));
        assertThat(annotations1.get(1), isA(YamlIgnoreCase.class));
        // (2) is some apiguardian?

        final List<Annotation> annotations2 = Reflection.getAnnotationsOn(Reflection.getField(ReflectionTest.class, "withTestInterface").orElseThrow());
        assertThat(annotations2.size(), is(2));
        assertThat(annotations2.get(0), isA(TestInterface.class));
        assertThat(annotations2.get(1), isA(TestName.class));
        assertThat(((TestInterface)annotations2.get(0)).name(), is("name1"));
        assertThat(((TestName)annotations2.get(1)).value(), is("some-test-name"));
    }
    @Test void getAnnotation() {
        assertThat(Reflection.getAnnotation(ReflectionTest.class, DisplayName.class).orElseThrow(), any(DisplayName.class));
        assertThat(Reflection.getAnnotation(ReflectionTest.class, Builder.class).isPresent(), is(false));
        assertThat(Reflection.getAnnotation(Reflection.getField(ReflectionTest.class, "withTestInterface").orElseThrow(), TestName.class)
            .map(TestName::value).orElse("<not found>"),is("some-test-name"));
    }
    @Test void hasAnnotation() {
        assertThat(Reflection.hasAnnotation(ReflectionTest.class, DisplayName.class), is(true));
        assertThat(Reflection.hasAnnotation(ReflectionTest.class, Builder.class), is(false));
    }
    @Test void addAnnotation() throws NoSuchFieldException, NoSuchMethodException {
        assertThat(Reflection.getAnnotation(ReflectionTest.class, Deprecated.class).isPresent(), is(false));
        assertThat(Reflection.hasAnnotation(ReflectionTest.class, Deprecated.class), is(false));
        Reflection.addAnnotation(ReflectionTest.class, Deprecated.class);
        assertThat(Reflection.getAnnotation(ReflectionTest.class, Deprecated.class).isPresent(), is(true));
        assertThat(Reflection.hasAnnotation(ReflectionTest.class, Deprecated.class), is(true));

        assertThat(Reflection.getAnnotation(ReflectionTest.class.getField("testField"), Deprecated.class).isPresent(), is(false));
        assertThat(Reflection.hasAnnotation(ReflectionTest.class.getField("testField"), Deprecated.class), is(false));
        Reflection.addAnnotation(ReflectionTest.class.getField("testField"), Deprecated.class);
        assertThat(Reflection.getAnnotation(ReflectionTest.class.getField("testField"), Deprecated.class).isPresent(), is(true));
        assertThat(Reflection.hasAnnotation(ReflectionTest.class.getField("testField"), Deprecated.class), is(true));

        assertThat(Reflection.getAnnotation(ReflectionTest.class.getMethod("setup"), Deprecated.class).isPresent(), is(false));
        assertThat(Reflection.hasAnnotation(ReflectionTest.class.getMethod("setup"), Deprecated.class), is(false));
        Reflection.addAnnotation(ReflectionTest.class.getMethod("setup"), Deprecated.class);
        assertThat(Reflection.getAnnotation(ReflectionTest.class.getMethod("setup"), Deprecated.class).isPresent(), is(true));
        assertThat(Reflection.hasAnnotation(ReflectionTest.class.getMethod("setup"), Deprecated.class), is(true));
    }
    @Test void removeAddedAnnotationsFrom() {
        Reflection.addAnnotation(ReflectionTest.class, Deprecated.class);
        assertThat(Reflection.getAnnotation(ReflectionTest.class, Deprecated.class).isPresent(), is(true));
        Reflection.removeAddedAnnotations();
        assertThat(Reflection.getAnnotation(ReflectionTest.class, Deprecated.class).isPresent(), is(false));
    }

    @Test void getAnnotationOnFieldOrParent() {
        final Field field = Reflection.getField(ReflectionTest.class, "testField").orElseThrow();
        assertThat(Reflection.getAnnotationOnElementOrParent(field, DisplayName.class).orElseThrow(), any(DisplayName.class));
        assertThat(Reflection.getAnnotationOnElementOrParent(field, Deprecated.class).isPresent(), is(false));
        assertThat(Reflection.getAnnotationOnElementOrParent(null, Deprecated.class).isPresent(), is(false));
        Reflection.addAnnotation(field, Deprecated.class, Tuple.of("since", "foobar"));
        assertThat(Reflection.getAnnotationOnElementOrParent(field, Deprecated.class).orElseThrow(), any(Deprecated.class));
        assertThat(Reflection.getAnnotationOnElementOrParent(field, Deprecated.class).orElseThrow().since(), is("foobar"));
        Reflection.removeAddedAnnotationsFrom(field);
        assertThat(Reflection.getAnnotationOnElementOrParent(field, Deprecated.class).isPresent(), is(false));
        Reflection.addAnnotation(ReflectionTest.class, Deprecated.class);
        assertThat(Reflection.getAnnotationOnElementOrParent(field, Deprecated.class).isPresent(), is(true));
    }
    @Test void getAnnotationOnMethodOrParent() {
        final Method method = Reflection.getMethod(ReflectionTest.class, "testMethod", Integer.TYPE, String.class).orElseThrow();
        assertThat(Reflection.getAnnotationOnElementOrParent(method, DisplayName.class).orElseThrow(), any(DisplayName.class));
        assertThat(Reflection.getAnnotationOnElementOrParent(method, YamlIgnoreCase.class).orElseThrow(), any(YamlIgnoreCase.class));
        assertThat(Reflection.getAnnotationOnElementOrParent(method, Test.class).isPresent(), is(false));
        Reflection.addAnnotation(method, Test.class);
        assertThat(Reflection.getAnnotationOnElementOrParent(method, Test.class).orElseThrow(), any(Test.class));
        Reflection.removeAddedAnnotationsFrom(method);
        assertThat(Reflection.getAnnotationOnElementOrParent(method, Test.class).isPresent(), is(false));
        Reflection.addAnnotation(ReflectionTest.class, Test.class);
        assertThat(Reflection.getAnnotationOnElementOrParent(method, Test.class).isPresent(), is(true));
    }
    @Test void getAnnotationOnInnerClassOrParent() {
        assertThat(Reflection.getAnnotationOnElementOrParent(InnerClass.class, DisplayName.class).orElseThrow(), any(DisplayName.class));
        assertThat(Reflection.getAnnotationOnElementOrParent(InnerClass.class, YamlIgnoreCase.class).orElseThrow(), any(YamlIgnoreCase.class));
        assertThat(Reflection.getAnnotationOnElementOrParent(InnerClass.class, Test.class).isPresent(), is(false));
        Reflection.addAnnotation(InnerClass.class, Test.class);
        assertThat(Reflection.getAnnotationOnElementOrParent(InnerClass.class, Test.class).orElseThrow(), any(Test.class));
        Reflection.removeAddedAnnotationsFrom(InnerClass.class);
        assertThat(Reflection.getAnnotationOnElementOrParent(InnerClass.class, Test.class).isPresent(), is(false));
        Reflection.addAnnotation(ReflectionTest.class, Test.class);
        assertThat(Reflection.getAnnotationOnElementOrParent(InnerClass.class, Test.class).isPresent(), is(true));
    }

    @Test void elementOrParentHasAnnotation() {
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, DisplayName.class), is(true));
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, YamlIgnoreCase.class), is(true));
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, Deprecated.class), is(false));
    }
    @Test void elementOrParentHasAnnotationValidated() {
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, DisplayName.class, gs -> true), is(true));
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, DisplayName.class, gs -> false), is(false));
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, YamlIgnoreCase.class, gs -> true), is(true));
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, YamlIgnoreCase.class, gs -> false), is(false));
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, Deprecated.class, gs -> true), is(false));
        Reflection.addAnnotation(InnerClass.class, Deprecated.class);
        assertThat(Reflection.elementOrParentHasAnnotation(InnerClass.class, Deprecated.class, gs -> true), is(true));
    }

    @Test void getAnnotations() {
        final Method method = Reflection.getMethod(ReflectionTest.class, "testMethod", Integer.TYPE, String.class).orElseThrow();

        assertThat(Reflection.getAnnotations(null, YamlIgnoreCase.class), empty());
        assertThat(Reflection.getAnnotations(this, YamlIgnoreCase.class), hasSize(1));
        assertThat(Reflection.getAnnotations(getClass(), YamlIgnoreCase.class), everyItem(any(YamlIgnoreCase.class)));
        assertThat(Reflection.getAnnotations(this, Deprecated.class), empty());
        assertThat(Reflection.getAnnotations(method, YamlIgnoreCase.class), hasSize(1));
        assertThat(Reflection.getAnnotations(method, Deprecated.class), empty());
    }
    @Test void getAnnotationsOnElementOrParent() {
        final Field field = Reflection.getField(ReflectionTest.class, "testField").orElseThrow();
        final Method method = Reflection.getMethod(ReflectionTest.class, "testMethod", Integer.TYPE, String.class).orElseThrow();
        assertThat(Reflection.getAnnotationsOnElementOrParent(null, Deprecated.class), empty());

        assertThat(Reflection.getAnnotationsOnElementOrParent(InnerClass.class, DisplayName.class), hasSize(1));
        assertThat(Reflection.getAnnotationsOnElementOrParent(InnerClass.class, YamlIgnoreCase.class), hasSize(1));
        assertThat(Reflection.getAnnotationsOnElementOrParent(InnerClass.class, Deprecated.class), empty());
        Reflection.addAnnotation(InnerClass.class, Deprecated.class);
        assertThat(Reflection.getAnnotationsOnElementOrParent(InnerClass.class, Deprecated.class), hasSize(1));
        Reflection.addAnnotation(InnerClass.class, YamlIgnoreCase.class);
        assertThat(Reflection.getAnnotationsOnElementOrParent(InnerClass.class, YamlIgnoreCase.class), hasSize(2));
        assertThat(Reflection.getAnnotationsOnElementOrParent(InnerClass.class, YamlIgnoreCase.class), everyItem(any(YamlIgnoreCase.class)));

        assertThat(Reflection.getAnnotationsOnElementOrParent(method, YamlIgnoreCase.class), everyItem(any(YamlIgnoreCase.class)));
        assertThat(Reflection.getAnnotationsOnElementOrParent(method, DisplayName.class), hasSize(1));
        assertThat(Reflection.getAnnotationsOnElementOrParent(method, Deprecated.class), empty());
        Reflection.addAnnotation(method, DisplayName.class, Tuple.of("value", "foobar"));
        assertThat(Reflection.getAnnotationsOnElementOrParent(method, DisplayName.class), hasSize(2));
        assertThat(Reflection.getAnnotationsOnElementOrParent(method, DisplayName.class).get(0).value(), is("foobar"));
        assertThat(Reflection.getAnnotationsOnElementOrParent(method, DisplayName.class).get(1).value(), is("ReflectionTest"));

        assertThat(Reflection.getAnnotationsOnElementOrParent(field, Deprecated.class), empty());
        assertThat(Reflection.getAnnotationsOnElementOrParent(field, DisplayName.class), hasSize(1));
    }

    @Test void createAnnotation() {
        final Deprecated props = Reflection.createAnnotation(Deprecated.class, Tuple.of("since", "abc"));
        final Deprecated props2 = Reflection.createAnnotation(Deprecated.class, Tuple.of("since", 123));
        assertThat(props.annotationType(), is(Deprecated.class));
        assertThat(props.since(), is("abc"));
        assertThat(props.equals(props2), is(false));
        assertThat(props2.since(), is(""));
    }

    @Test void invoke() {
        final Method methodA = Reflection.getMethod(ReflectionTest.class, "testMethod", Integer.TYPE).orElseThrow();
        final Method methodAB = Reflection.getMethod(ReflectionTest.class, "testMethod", Integer.TYPE, String.class).orElseThrow();
        final Method methodABC = Reflection.getMethod(ReflectionTest.class, "testMethod", Integer.TYPE, String.class, Integer.TYPE).orElseThrow();
        final Method methodDoThrow = Reflection.getMethod(ReflectionTest.class, "doThrow").orElseThrow();
        assertThat(Reflection.invoke(this, methodAB, 123, "abc"), is(Value.of("123abc")));
        assertThat(Reflection.invoke(this, methodAB,   0, "abc").isAbsent(), is(true));
        assertThat(Reflection.invoke(this, methodA, 123, e -> "999"), is("@123"));
        assertThat(Reflection.invoke(this, methodA,   0, e -> "999"), is("999"));
        assertThat(Reflection.invoke(this, methodAB, 123, "abc", e -> "999"), is("123abc"));
        assertThat(Reflection.invoke(this, methodAB,   0, "abc", e -> "999"), is("999"));
        assertThat(Reflection.invoke(this, methodABC, new Object[] { 123, "abc", 456 }, e -> "999"), is("123abc456"));
        assertThat(Reflection.invoke(this, methodABC, new Object[] {   0, "abc", 456 }, e -> "999"), is("999"));
        assertThat(Reflection.invoke(this, methodDoThrow, ex -> "999"), is("999"));
    }
    @Test void getConstructor() {
        assertThat(Reflection.getConstructor(InnerClass.class, Integer.TYPE, String.class).isPresent(), is(true));
        assertThat(Reflection.getConstructor(InnerClass.class).isPresent(), is(true));
        assertThat(Reflection.getConstructor(InnerClass.class, Integer.TYPE).isPresent(), is(false));
    }
    @Test void hasDefaultConstructor() {
        assertThat(Reflection.hasDefaultConstructor(InnerClass.class), is(true));
        assertThat(Reflection.hasDefaultConstructor(InnerClassEmpty.class), is(true));
        assertThat(Reflection.hasDefaultConstructor(InnerClassWithString.class), is(false));
    }
    @Test void construct() {
        final Constructor<InnerClass> con = Reflection.getConstructor(InnerClass.class, Integer.TYPE, String.class).orElseThrow();
        assertThat(Reflection.construct(InnerClass.class).orElseThrow(), any(InnerClass.class));
        assertThat(Reflection.construct(con, 123, "abc").orElseThrow(), any(InnerClass.class));
        assertThat(Reflection.construct(con, 0, "abc").isPresent(), is(false));

        final boolean[] handlerCalled = { false };
        assertThat(Reflection.construct(con, new Object[] { 0, "abc" }, ex -> {
              assertThat(ex.getMessage(), containsString("a is 0"));
              handlerCalled[0] = true;
              return new InnerClass();
        }), any(InnerClass.class));
        assertThat(handlerCalled[0], is(true)); handlerCalled[0] = false;

        assertThat(Reflection.construct(con, new Object[] { 0, "abc", "wrongNumberOfArgs" }, ex -> {
            handlerCalled[0] = true;
            return new InnerClass();
        } ), any(InnerClass.class));
        assertThat(handlerCalled[0], is(true)); handlerCalled[0] = false;

        assertThat(Reflection.construct(ThrowingClass.class).isPresent(), is(false));
        assertThat(Reflection.construct(PrivateConstructorClass.class).isPresent(), is(false));
        assertThat(Reflection.construct(NoDefaultConstructorClass.class).isPresent(), is(false));
    }

    @Test void hasAllModifiers() {
        assertThat(Reflection.hasAllModifiers(1 | 4 | 8 | 32, 1, 4, 8), is(true));
        assertThat(Reflection.hasAllModifiers(1 | 4 | 8 | 32, 1, 8, 16), is(false));
    }
    @Test void hasAnyModifiers() {
        assertThat(Reflection.hasAnyModifiers(1 | 4 | 8, 4), is(true));
        assertThat(Reflection.hasAnyModifiers(1 | 4 | 8, 2, 16, 32), is(false));
    }

    @Test void isStatic() {
        final Method method = Reflection.getMethod(getClass(), "testMethod").orElseThrow();
        final Method staticMethod = Reflection.getMethod(getClass(), "staticTestMethod").orElseThrow();
        assertThat(Reflection.isStatic(method), is(false));
        assertThat(Reflection.isStatic(staticMethod), is(true));
    }
    @Test void isPublic() throws NoSuchMethodException {
        final Method method = Reflection.getMethod(getClass(), "testMethod").orElseThrow();
        final Method privateMethod = getClass().getDeclaredMethod("privateMethod");
        assertThat(Reflection.isPublic(method), is(true));
        assertThat(Reflection.isPublic(privateMethod), is(false));
    }
}