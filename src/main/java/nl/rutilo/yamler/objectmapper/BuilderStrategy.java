package nl.rutilo.yamler.objectmapper;

import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Reflection;
import nl.rutilo.yamler.utils.StringUtils;
import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.exceptions.YamlerException;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class BuilderStrategy<T> extends ReflectionStrategy<T> {
    public static <T> Value<BuilderStrategy<T>> createFor(Class<T> clazz, final List<String> messages) {
        final Value<Method> optGetBuilder = Reflection.getMethod(clazz, "builder");
        final Value<Class<?>> optBuilderType = optGetBuilder.map(Method::getReturnType);
        final Value<Method> optBuildMethod = optBuilderType.flatMap(bt -> Reflection.getMethod(bt, "build"));
        final Value<List<KVInfo>> optSetters = optBuilderType.map(bt -> Stream.of(bt.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .filter(method -> method.getParameterCount() == 1)
            .filter(method -> !"build".equals(method.getName()))
            .sorted(Comparator.comparing(Method::getName, Comparator.naturalOrder())) // reflection has no order
            // Builder to set. To get a value, there should be (1) a public field or (2) a public getter
            .map(method -> Value.orSupplyValue(
                () -> Reflection.getField(clazz, method.getName()).map(field -> new KVInfoFieldSetter(field, method)),
                () -> Reflection.getMethod(clazz, "get" + StringUtils.ucFirst(method.getName())).map(getter -> new KVInfoGetterSetter(getter, method))
                )
            )
            .flatMap(Value::stream)
            .filter(kv -> !kv.isIgnored)
            .toList()
        ).filter(list -> !list.isEmpty());
        optGetBuilder.ifPresentOrElse(
            gb -> optBuildMethod.ifPresentOrElse(
                b -> optSetters.ifPresentOrElse(s -> {
                }, () -> messages.add("Builder found, but no public getters or public fields found")),
                () -> messages.add("No build() method found on the builder class")),
            () -> messages.add("No builder() method found")
        );
        return Value.combine(optGetBuilder, optBuildMethod, optSetters, (bm, gb, s) -> new BuilderStrategy<>(clazz, bm, gb, s));
    }

    private final Method getBuilder;
    private final Method build;

    public BuilderStrategy(Class<T> clazz, Method getBuilder, Method build, List<KVInfo> setters) {
        super(clazz, setters);
        this.getBuilder = getBuilder;
        this.build = build;
    }

    public T createObjectFrom(StringKeyMap dataMap) {
        final Object builder = Reflection.invoke(null, getBuilder, cause -> {
            throw new YamlerException("Unable to create builder for " + clazz, cause);
        });
        kvInfos.forEach(setter -> setter.setValueIn(dataMap, builder));
        return Reflection.invoke(builder, build, cause -> {
            throw new YamlerException("Unable to build a " + clazz, cause);
        });
    }
}
