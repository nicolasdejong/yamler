package nl.rutilo.yamler.objectmapper;

import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Reflection;
import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.annotations.CustomMapper;
import nl.rutilo.yamler.yamler.exceptions.YamlerException;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class CustomMapperStrategy<T> extends ReflectionStrategy<T> {
    private final Method fromMap;
    private final Method toMap;

    public static <T> Value<CustomMapperStrategy<T>> createFor(Class<T> targetClass, final List<String> messages) {
        return Value.of(targetClass.getAnnotation(CustomMapper.class))
            .flatMap(convInfo -> {
                try {
                    return createFor(targetClass, messages, convInfo);
                } catch (final Exception e) {
                    messages.add("CustomConverter error: " + e.getMessage());
                    return Value.empty();
                }
            });
    }
    private static <T> Value<CustomMapperStrategy<T>> createFor(Class<T> targetClass, List<String> messages, CustomMapper convInfo) {
        final Class<?> convClass = convInfo.converterClass() == Object.class ? targetClass : convInfo.converterClass();
        final Value<Method> fromJsonMap = Reflection.getMethod(convClass, convInfo.fromMap(), StringKeyMap.class)
            .filter(m -> Reflection.isStatic(m)
                || addMessage(messages, convClass.getName() + "::" + m.getName() + " should be static"))
            .filter(m -> (m.getParameterCount() == 1 && Map.class.isAssignableFrom(m.getParameterTypes()[0]))
                || addMessage(messages, convClass.getName() + "::" + m.getName() + " should have a single parameter of type Map or StringKeyMap"))
            ;
        final Value<Method> toJsonMap = Reflection.getMethod(convClass, convInfo.toMap())
            .filter(m -> (Reflection.isStatic(m) && m.getParameterCount() == 1 && targetClass.isAssignableFrom(m.getParameterTypes()[0]))
                || (!Reflection.isStatic(m) && m.getParameterCount() == 0 && convClass == targetClass)
                || addMessage(messages, convClass.getName() + "::" + m.getName() + " should be static and with single parameter of type " + targetClass.getName() + " or without arguments on class " + targetClass.getName()))
            ;
        if (fromJsonMap.isEmpty())  messages.add(convClass.getName() + "::" + convInfo.fromMap() + " not found");
        if (toJsonMap.isEmpty())    messages.add(convClass.getName() + "::" + convInfo.toMap() + " not found");

        return fromJsonMap.isPresent() && toJsonMap.isPresent()
            ? Value.of(new CustomMapperStrategy<>(targetClass, fromJsonMap.get(), toJsonMap.get()))
            : Value.empty();
    }
    private static boolean addMessage(List<String> messages, String message) {
        messages.add(message);
        return false;
    }

    private CustomMapperStrategy(Class<T> targetClass, Method fromMap, Method toMap) {
        super(targetClass, Collections.emptyList());
        this.fromMap = fromMap;
        this.toMap = toMap;
    }

    public T createObjectFrom(StringKeyMap dataMap) {
        try {
            //noinspection unchecked -- failure will immediately be caught below
            return (T) fromMap.invoke(null, dataMap);
        } catch (final Exception cause) {
            throw new YamlerException("Unable to create " + clazz.getName(), cause);
        }
    }

    @Override
    public StringKeyMap createMapFrom(Object instance) {
        try {
            final boolean isStatic = Reflection.isStatic(toMap);
            final Map<?, ?> map = (Map<?, ?>) toMap.invoke(isStatic ? null : instance, isStatic ? new Object[]{instance} : new Object[0]);
            return map instanceof StringKeyMap skMap ? skMap : StringKeyMap.from(map);
        } catch (final Exception cause) {
            throw new YamlerException("Unable to get map from " + clazz.getName(), cause);
        }
    }
}
