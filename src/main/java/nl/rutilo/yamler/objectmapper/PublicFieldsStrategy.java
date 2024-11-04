package nl.rutilo.yamler.objectmapper;

import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Reflection;
import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.exceptions.YamlerException;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.TRANSIENT;

final class PublicFieldsStrategy<T> extends ReflectionStrategy<T> {
    public static <T> Value<PublicFieldsStrategy<T>> createFor(Class<T> clazz, final List<String> messages) {
        return Value.of(
            Stream.of(clazz.getFields())
                .filter(f -> !Reflection.hasAnyModifiers(f.getModifiers(), STATIC, TRANSIENT, FINAL))
                .sorted(Comparator.comparing(Field::getName, Comparator.naturalOrder())) // reflection has no order
                .map(KVInfoField::new)
                .filter(kv -> !kv.isIgnored)
                .toList()
            ).filter(n -> {
                if (n.isEmpty()) {
                    messages.add("No public non-final fields found");
                    return false;
                } else return true;
            })
             .filter(n -> {
                    if (Reflection.hasDefaultConstructor(clazz)) return true;
                    else {
                        messages.add("Public fields, but no default constructor");
                        return false;
                    }
                })
             .map(kvInfos -> new PublicFieldsStrategy<>(clazz, kvInfos));
    }

    private PublicFieldsStrategy(Class<T> clazz, List<KVInfoField> fieldInfos) {
        super(clazz, fieldInfos);
    }

    public T createObjectFrom(StringKeyMap dataMap) {
        final T obj = Reflection.construct(clazz).orElseThrow(() -> new YamlerException("Unable to create " + clazz));
        kvInfos.forEach(kvInfo -> kvInfo.setValueIn(dataMap, obj));
        return obj;
    }
}
