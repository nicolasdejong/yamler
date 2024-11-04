package nl.rutilo.yamler.objectmapper;

import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Reflection;
import nl.rutilo.yamler.utils.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class ConstructorStrategy<T> extends ReflectionStrategy<T> {
    public static <T> Value<ConstructorStrategy<T>> createFor(Class<T> clazz, final List<String> messages) {
        @SuppressWarnings("unchecked")
        final Value<Constructor<T>> optConstructor = Value.ofOptional(
          Stream.of((Constructor<T>[]) clazz.getConstructors())
            .filter(c -> c.getParameterCount() > 0)
            .max(Comparator.comparingInt(Constructor::getParameterCount)));
        final Value<List<KVInfoParamField>> optKVPairs = optConstructor
            .map(con -> Stream.of(con.getParameters()))
            .map(params -> params.filter(Parameter::isNamePresent)
                .map(param -> {
                    final Value<Field> field = Reflection.getField(clazz, param.getName());
                    return field.map(f -> new KVInfoParamField(param, f)).orElse(null);
                })
            )
            .map(Stream::toList)
            .filter(list -> !list.isEmpty() && !list.contains(null));
        optConstructor.ifPresentOrElse(
            c -> optKVPairs.ifAbsent(() -> messages.add("Constructor params could not be connected to any public fields (requires javac -parameters option and equal names)")),
            () -> messages.add("No constructor with parameters found")
        );
        // TODO: add fallback by finding a constructor that has the params in the same order as the fields with the same types
        return Value.combine(
            optConstructor, optKVPairs,
            (constructor, kvPairs) -> new ConstructorStrategy<>(clazz, constructor, kvPairs));
    }

    final Constructor<T> constructor;

    private ConstructorStrategy(Class<T> clazz, Constructor<T> constructor, List<KVInfoParamField> kvPairs) {
        super(clazz, kvPairs);
        this.constructor = constructor;
    }

    @Override
    public T createObjectFrom(StringKeyMap dataMap) {
        return Reflection.construct(constructor, kvInfos.stream()
                .map(kvPair -> kvPair.getValueFrom(dataMap).orElseGet(kvPair::getDefaultValue))
                .toArray(),
            exception -> {
                throw RSObjectMapper.unableToCreate(clazz, dataMap);
            });
    }
}
