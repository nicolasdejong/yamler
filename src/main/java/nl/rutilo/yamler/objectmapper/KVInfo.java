package nl.rutilo.yamler.objectmapper;

import lombok.EqualsAndHashCode;
import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Value;
import nl.rutilo.yamler.yamler.annotations.YamlIgnore;
import nl.rutilo.yamler.yamler.annotations.YamlIgnoreCase;
import nl.rutilo.yamler.yamler.annotations.YamlName;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EqualsAndHashCode
abstract class KVInfo implements Comparable<KVInfo> {
    public final Class<?> ownerClass;
    public final Class<?> valueType;
    public final Type genericType;
    public final String name;
    public final Map<Class<?>, Annotation> annotations;
    public final boolean isIgnored;

    protected KVInfo(Class<?> ownerClass, Class<?> valueType, Type genericType, String name, List<Annotation> annotations) {
        this.ownerClass = ownerClass;
        this.valueType = valueType;
        this.genericType = genericType;
        this.name = name;
        this.annotations = Stream.concat(annotations.stream(), Arrays.stream(ownerClass.getAnnotations()))
            .filter(an -> an.annotationType().getPackageName().endsWith(".yamler.annotations"))
            .collect(Collectors.toMap(Annotation::annotationType, a -> a));
        this.isIgnored = getAnnotation(YamlIgnore.class).map(ig -> ig.value().length == 0 || Arrays.asList(ig.value()).contains(name)).orElse(false);
    }

    @Override
    public int compareTo(KVInfo other) {
        return name.compareTo(other.name);
    }

    public final <T> Value<T> getAnnotation(Class<T> clazz) {
        //noinspection unchecked
        return Value.of((T) annotations.get(clazz));
    }

    public Value<Object> getValueFrom(StringKeyMap dataMap) {
        final String useName = getAnnotation(YamlName.class).map(YamlName::value).orElse(name);
        final Value<Object> value =
          isIgnored ? Value.absent()
                    : annotations.containsKey(YamlIgnoreCase.class)
                        ? dataMap.getValueIgnoreCase(useName)
                        : dataMap.getValue(useName);
        return value.map(rawVal -> RSObjectMapper.toTargetObject(rawVal, valueType, genericType));
    }

    public Object getDefaultValue() {
        return RSObjectMapper.toTargetObject(null, valueType, genericType);
    }

    public abstract Object getValueFrom(Object obj);

    public abstract void setValueIn(StringKeyMap source, Object target);
}
