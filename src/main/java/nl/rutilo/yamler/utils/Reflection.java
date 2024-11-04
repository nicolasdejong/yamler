package nl.rutilo.yamler.utils;

import nl.rutilo.yamler.collections.Collections2;
import nl.rutilo.yamler.collections.GeneratingMap;
import nl.rutilo.yamler.utils.Tuple.Tuple2;
import nl.rutilo.yamler.utils.throwingfunctionals.ThrowingPredicate;

import java.lang.StackWalker.StackFrame;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public final class Reflection {
    private static final Map<Object,List<Annotation>> targetToExtraAnnotations = new GeneratingMap<>(ArrayList::new);
    private Reflection() {}

    public static Class<?>          getCallerClass() {
        return getClass(getCallerStackTraceFrame().getClassName()).orElseThrow();
    }
    public static Class<?>          getCallerClass(Class<?>... excludeClasses) {
        return getClass(getCallerStackTraceFrame(excludeClasses).getClassName()).orElseThrow();
    }
    public static Class<?>          getCallerClass(String... excludePakOrClassPattern) {
        return getClass(getCallerStackTraceFrame(excludePakOrClassPattern).getClassName()).orElseThrow();
    }

    public static String     getCallerId(Class<?>... excludeClasses) {
        final StackFrame sf = getCallerStackTraceFrame(excludeClasses);
        return sf.getClassName() + "." + sf.getMethodName() + ":" + sf.getByteCodeIndex();
    }
    public static StackFrame getCallerStackTraceFrame() {
        return getCallerStackTraceFrame(new Class<?>[0]);
    }
    public static StackFrame getCallerStackTraceFrame(Class<?>... excludeClasses) {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream -> stream
            .filter(st -> !st.getClassName().equals(Reflection.class.getName()))
            .filter(st -> !Reflection.isJavaClass(st.getDeclaringClass()))
            .filter(st -> Arrays.stream(excludeClasses).noneMatch(cl -> cl.getName().equals(st.getClassName())))
            .findFirst()
            .orElseThrow()
        );
    }
    public static StackFrame getCallerStackTraceFrame(String... excludePakOrClassPattern) {
        final String excludeRegex =
            "^(" +
            Arrays.stream(excludePakOrClassPattern)
            .map(pat -> pat.replace(".", "\\.").replace("*", "(.*)"))
            .collect(Collectors.joining(")|("))
            + ")$";
        final Pattern excludePattern = Pattern.compile(excludeRegex);

        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream -> stream
            .filter(st -> !st.getClassName().equals(Reflection.class.getName()))
            .filter(st -> !Reflection.isJavaClass(st.getDeclaringClass()))
            .filter(st -> !excludePattern.matcher(st.getClassName()).matches())
            .findFirst()
            .orElseThrow()
        );
    }

    public static boolean isJavaClass(StackTraceElement st) {
        return isJavaClass(st.getClassName());
    }
    public static boolean isJavaClass(Class<?> clazz) {
        return isJavaClass(clazz.getName());
    }
    public static boolean isJavaClass(String className) {
        return className.startsWith("jdk.")
            || className.startsWith("java.")
            || className.startsWith("javax.")
            || className.contains(".internal.");
    }

    public static Value<Class<?>>  getClass         (String className) {
        return Value.supply(() -> Class.forName(className));
    }
    public static Value<Method>    getMethod        (Class<?> clazz, String name, Class<?>... paramTypes) {
        return Value.supply(() -> clazz.getMethod(name, paramTypes));
    }
    public static Value<Object>    getDefaultValueOf(Method method) {
        return Value.ofNotNullable(method).map(Method::getDefaultValue);
    }
    public static Value<Field>     getField         (Class<?> clazz, String name) {
        return Value.supply(() -> clazz.getField(name));
    }
    public static Value<Object>    getFieldValue    (Class<?> clazz, String name, Object target) {
        return getField(clazz, name).map(f -> f.get(target));
    }
    public static Value<Object>    getFieldValue    (Object obj, String fieldName) {
        return obj == null ? Value.empty() : getFieldValue(obj, getField(obj.getClass(), fieldName).orElseThrow());
    }
    public static Value<Object>    getFieldValue    (Object obj, Field field) {
        return Value.supply(() -> field.get(obj));
    }
    public static boolean          setFieldValue    (Object obj, Field field, Object value) {
        return setFieldValue(obj, field, value, null);
    }
    public static boolean          setFieldValue    (Object obj, Field field, Object value, Consumer<Exception> exceptionHandler) {
        try {
            field.set(obj, value); // NOSONAR -- this is reflection but only for public fields
            return true;
        } catch (final IllegalAccessException e) {
            if (exceptionHandler != null) exceptionHandler.accept(e);
            return false;
        }
    }
    public static boolean          setFieldValue    (Object obj, String fieldName, Object value) {
        return getField(obj.getClass(), fieldName)
            .map(field -> setFieldValue(obj, field, value, null))
            .orElse(false);
    }
    public static boolean          setFieldValue    (Object obj, String fieldName, Object value, Consumer<Exception> exceptionHandler) {
        return getField(obj.getClass(), fieldName)
            .map(field -> setFieldValue(obj, field, value, exceptionHandler))
            .orElse(false);
    }
    public static Value<Parameter> getParameter     (Method method, int n) {
        return method == null || n >= method.getParameterCount() ? Value.empty() : Value.of(method.getParameters()[n]);
    }
    public static List<String>     getParameterNames(Method method) {
        return method == null ? Collections.emptyList() :
            Arrays.stream(method.getParameters()).map(Parameter::getName).toList();
    }
    public static List<Class<?>>   getParameterTypes(Method method) {
        return method == null ? Collections.emptyList() :
            Arrays.stream(method.getParameters()).map(Parameter::getType).collect(Collectors.toList()); // NOSONAR just .toList() mangles type
    }

    public static boolean hasInterface(Class<?> target, Class<?> interfaceToFind) {
        return Arrays.stream(target.getInterfaces()).anyMatch(interf -> interfaceToFind == interf);
    }

    /** Add an annotation that will be found by the getAnnotation...() methods in this Reflection class only */
    @SafeVarargs
    public static <A extends Annotation> void addAnnotation(Class<?> target, Class<A> annotationType, Tuple2<String,Object>... attributes) {
        addAnnotationLocal(target, annotationType, attributes);
    }
    @SafeVarargs
    public static <A extends Annotation> void addAnnotation(Method target, Class<A> annotationType, Tuple2<String,Object>... attributes) {
        addAnnotationLocal(target, annotationType, attributes);
    }
    @SafeVarargs
    public static <A extends Annotation> void addAnnotation(Field target, Class<A> annotationType, Tuple2<String,Object>... attributes) {
        addAnnotationLocal(target, annotationType, attributes);
    }
    @SafeVarargs
    private static <A extends Annotation> void addAnnotationLocal(Object target, Class<A> annotationType, Tuple2<String,Object>... attributes) {
        synchronized (targetToExtraAnnotations) {
            final Object ann = createAnnotation(annotationType, attributes);
            final List<?> list= targetToExtraAnnotations.get(target);
            targetToExtraAnnotations.get(target).add(createAnnotation(annotationType, attributes));
        }
    }

    public static void removeAddedAnnotationsFrom(Object target) {
        synchronized (targetToExtraAnnotations) {
            targetToExtraAnnotations.remove(target);
        }
    }
    static void removeAddedAnnotations() {
        synchronized (targetToExtraAnnotations) {
            targetToExtraAnnotations.clear();
        }
    }
    private static <A extends Annotation> List<A> getAddedAnnotations(Object target, Value<Class<A>> annotationType) {
        final List<Annotation> annotations;
        synchronized (targetToExtraAnnotations) {
            annotations = targetToExtraAnnotations.getOrDefault(target, Collections.emptyList());
        }
        //noinspection unchecked
        return (List<A>)annotations.stream()
            .filter(a -> annotationType.map(annType -> annType == a.annotationType()).orElse(true))
            .toList();
    }

    private static List<Annotation> getAnnotationsOnLocal(AnnotatedElement element) { return getAnnotationsOnLocal(element, new HashSet<>()); }
    private static List<Annotation> getAnnotationsOnLocal(AnnotatedElement element, Set<Annotation> foundAnnotations) {
        if (element == null) return Collections.emptyList();
        final Predicate<Annotation> notAlreadyAdded = ann -> !foundAnnotations.contains(ann);
        final Predicate<Annotation> notJavaAnnotation = ann -> !ann.annotationType().getName().startsWith("java.lang.");

        final List<Annotation> annotations = Stream.concat(Stream.of(element.getAnnotations()), getAddedAnnotations(element, Value.empty()).stream())
            .filter(notAlreadyAdded)
            .collect(Collectors.toList());

        foundAnnotations.addAll(annotations);

        final Set<Annotation> deeperAnnotations = new HashSet<>();
        annotations.forEach(annotation -> getAnnotationsOnLocal(annotation.annotationType(), foundAnnotations).stream()
            .filter(notJavaAnnotation)
            .forEach(deeperAnnotations::add)
        );
        annotations.addAll(deeperAnnotations);

        return annotations;
    }
    public static List<Annotation> getAnnotationsOn(AnnotatedElement... elements) {
        return Stream.of(elements)
            .filter(Objects::nonNull)
            .flatMap(element -> Reflection.getAnnotationsOnLocal(element).stream())
            .toList();
    }
    public static <A extends Annotation> Value<A> getAnnotation(AnnotatedElement element, Class<A> annotationType) {
        if (element == null || annotationType == null) return Value.empty();

        //noinspection unchecked
        return (Value<A>) VStream.of(getAnnotationsOnLocal(element).stream())
            .filter(anno -> annotationType == anno.annotationType())
            .findFirst();
    }

    public static <A extends Annotation> boolean hasAnnotation(AnnotatedElement target, Class<A> annotationToFind) {
        if(target == null || annotationToFind == null) return false;
        return target.getAnnotationsByType(annotationToFind).length != 0
            || targetToExtraAnnotations.get(target).stream().anyMatch(an -> an.annotationType().isAssignableFrom(annotationToFind));
    }

    public static <T extends Annotation> Value<T> getAnnotationOnElementOrParent(AnnotatedElement element, Class<T> annotationType) {
        return Value
            .orSupplyValue(() -> getAnnotation(element, annotationType),
                           () -> (element instanceof Member  member ? getAnnotation((member).getDeclaringClass(), annotationType) :
                                 (element instanceof Class<?> clazz ? getAnnotation((clazz).getDeclaringClass(), annotationType) :
                                 Value.absent()))
            );
    }
    public static <T extends Annotation> boolean elementOrParentHasAnnotation(AnnotatedElement element, Class<T> annotationType) {
        return elementOrParentHasAnnotation(element, annotationType, a -> true);
    }
    public static <T extends Annotation> boolean elementOrParentHasAnnotation(AnnotatedElement element, Class<T> annotationType, ThrowingPredicate<T> validator) {
        return getAnnotationOnElementOrParent(element, annotationType)
            .filter(validator)
            .isPresent();
    }

    public static <T extends Annotation> List<T> getAnnotations(Object holder, Class<T> annotationType) {
        return holder == null ? Collections.emptyList() : getAnnotations(holder.getClass(), annotationType);
    }
    public static <T extends Annotation> List<T> getAnnotations(AnnotatedElement element, Class<T> annotationType) {
        //noinspection unchecked
        return getAnnotationsOnLocal(element).stream()
            .filter(anno -> anno.annotationType() == annotationType)
            .map(anno -> (T)anno) // instanceOf on previous line
            .toList();
    }
    public static <T extends Annotation> List<T> getAnnotationsOnElementOrParent(AnnotatedElement element, Class<T> annotationType) {
        return Collections2.concat(
            getAnnotations(element, annotationType),
            element instanceof Member  member ? getAnnotations(member.getDeclaringClass(), annotationType) : null,
            element instanceof Class<?> clazz ? getAnnotations(clazz.getDeclaringClass(), annotationType) : null
        );
    }

    @SafeVarargs @SuppressWarnings("unchecked")
    public static <A extends Annotation> A createAnnotation(Class<A> type, Tuple2<String,Object>... attributes) {
        return (A) Proxy.newProxyInstance(Reflection.class.getClassLoader(), new Class[]{type},
            (proxy, method, args) ->
                switch (method.getName()) {
                    case "toString" -> "[" + type.getSimpleName() + Arrays.stream(attributes)
                        .map(t -> t.a + "=" + t.b)
                        .reduce("", (a, b) -> a + "," + b) + "]";
                    case "annotationType" -> type;
                    case "equals" -> false;
                    case "hashCode" -> 0;
                    default -> valueOfAnnotationAttribute(method, attributes);
            });
    }

    @SuppressWarnings("unchecked")
    private static Object valueOfAnnotationAttribute(Method method, Tuple2<String,Object>... tuples) {
        return Arrays.stream(tuples)
            .filter(tuple -> method.getName().equals(tuple.a))
            .map(tuple -> tuple.b)
            .map(value -> valueOfTypeOrNull(value, method.getReturnType()))
            .filter(Objects::nonNull)
            .findFirst()
            .orElseGet(method::getDefaultValue);
    }
    private static Object valueOfTypeOrNull(Object value, Class<?> type) {
        if(value == null) return null;
        if(type.isAssignableFrom(value.getClass())) return value;
        if(type.isPrimitive() && value.getClass().getSimpleName().equalsIgnoreCase(type.getName())) return value;
        return null;
    }

    public static <T> Value<T> invoke(Object obj, Method method, Object... params) {
        //noinspection unchecked
        return Value.supply(() -> (T) method.invoke(obj, params));
    }
    public static <T> T invoke(Object obj, Method method, Function<Exception, T> exceptionHandler) {
        try {
            //noinspection unchecked
            return (T) method.invoke(obj);
        } catch (final Exception cause) {
            return exceptionHandler.apply(cause);
        }
    }
    public static <T> T invoke(Object obj, Method method, Object param, Function<Exception, T> exceptionHandler) {
        try {
            //noinspection unchecked
            return (T) method.invoke(obj, param);
        } catch (final Exception cause) {
            return exceptionHandler.apply(cause);
        }
    }
    public static <T> T invoke(Object obj, Method method, Object param1, Object param2, Function<Exception, T> exceptionHandler) {
        try {
            //noinspection unchecked
            return (T) method.invoke(obj, param1, param2);
        } catch (final Exception cause) {
            return exceptionHandler.apply(cause);
        }
    }
    public static <T> T invoke(Object obj, Method method, Object[] args, Function<Exception, T> exceptionHandler) {
        try {
            //noinspection unchecked
            return (T) method.invoke(obj, args);
        } catch (final Exception cause) {
            return exceptionHandler.apply(cause);
        }
    }

    public static <T> Value<Constructor<T>> getConstructor(Class<T> clazz, Class<?>... parameterTypes) {
        return Value.supply(() -> clazz.getConstructor(parameterTypes));
    }
    public static boolean hasDefaultConstructor(Class<?> clazz) {
        try {
            clazz.getConstructor();
        } catch (final NoSuchMethodException e) {
            return false;
        }
        return true;
    }
    public static <T> Value<T> construct(Class<T> clazz) {
        return Value.supply(() -> clazz.getConstructor().newInstance());
    }
    public static <T> Value<T> construct(Class<T> clazz, Object... initArgs) {
        return Value.supply(() -> clazz.getConstructor(VStream.ofArray(Object.class, initArgs).map(Object::getClass).toArray(Class[]::new)).newInstance(initArgs));
    }
    public static <T> Value<T> construct(Constructor<T> constr, Object... initArgs) {
        return Value.supply(() -> constr.newInstance(initArgs));
    }
    public static <T> T construct(Constructor<T> constr, Object[] initArgs, Function<Exception, T> exceptionHandler) {
        try {
            return constr.newInstance(initArgs);
        } catch (final InvocationTargetException ite) {
            return exceptionHandler.apply((Exception)ite.getTargetException());
        } catch (final Exception cause) {
            return exceptionHandler.apply(cause);
        }
    }

    public static boolean hasAllModifiers(int modifiers, int... flags) {
        return Arrays.stream(flags).allMatch(flag -> (modifiers & flag) != 0);
    }
    public static boolean hasAnyModifiers(int modifiers, int... flags) {
        if (flags.length == 0) return modifiers == 0;
        return Arrays.stream(flags).anyMatch(flag -> (modifiers & flag) != 0);
    }
    public static boolean isStatic(Method m) {
        return Modifier.isStatic(m.getModifiers());
    }
    public static boolean isPublic(Method m) {
        return Modifier.isPublic(m.getModifiers());
    }
}
