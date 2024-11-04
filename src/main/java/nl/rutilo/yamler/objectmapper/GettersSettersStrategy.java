package nl.rutilo.yamler.objectmapper;

import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Reflection;
import nl.rutilo.yamler.utils.StringUtils;
import nl.rutilo.yamler.utils.Value;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.reflect.Modifier.ABSTRACT;
import static java.lang.reflect.Modifier.STATIC;

final class GettersSettersStrategy<T> extends ReflectionStrategy<T> {
    public static <T> Value<GettersSettersStrategy<T>> createFor(Class<T> clazz, final List<String> messages) {
        if (!Reflection.hasDefaultConstructor(clazz)) {
            messages.add("No default constructor found for getters/setters class (inner class not static?)");
            return Value.absent();
        }
        final Set<String> fieldNames = Arrays.stream(clazz.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
        final List<KVInfoGetterSetter> kvInfos = Stream.of(clazz.getMethods())
            .filter(method -> method.getDeclaringClass() == clazz)
            .filter(method -> !Reflection.hasAnyModifiers(method.getModifiers(), STATIC, ABSTRACT))
            .filter(method -> method.getName().startsWith("get") && fieldNames.contains(StringUtils.lcFirst(method.getName().substring(3))) && method.getParameterCount() == 0)
            .map(getMethod -> {
                final String setName = "set" + getMethod.getName().substring(3);
                final Class<?> type = getMethod.getReturnType();
                return new KVInfoGetterSetter(
                    getMethod,
                    Reflection.getMethod(clazz, setName, type).orElse(null)
                );
            })
            .filter(kvInfo -> kvInfo.setMethod != null && !kvInfo.isIgnored)
            .sorted(Comparator.naturalOrder()) // reflection has no order
            .toList();
        if (kvInfos.isEmpty()) messages.add("No getters/setters found");
        return kvInfos.isEmpty() ? Value.absent() : Value.of(new GettersSettersStrategy<>(clazz, kvInfos));
    }

    private GettersSettersStrategy(Class<T> clazz, List<KVInfoGetterSetter> fieldInfos) {
        super(clazz, fieldInfos);
    }

    public T createObjectFrom(StringKeyMap dataMap) {
        final T obj = Reflection.construct(clazz).orElseThrow();
        kvInfos.forEach(kvInfo -> kvInfo.setValueIn(dataMap, obj));
        return obj;
    }
}
