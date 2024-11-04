package nl.rutilo.yamler.objectmapper;

import lombok.EqualsAndHashCode;
import nl.rutilo.yamler.collections.Collections2;
import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Reflection;
import nl.rutilo.yamler.utils.Value;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
class KVInfoField extends KVInfo {
    public final Field field;

    public KVInfoField(Field field) {
        this(field, null);
    }

    public KVInfoField(Field field, Annotation[] extraAnnotations) {
        super(field.getDeclaringClass(), field.getType(), field.getGenericType(),
            field.getName(),
            Collections2.concat(Reflection.getAnnotationsOn(field),
                                extraAnnotations == null ? null : List.of(extraAnnotations))
        );
        this.field = field;
    }

    public Value<Object> getValueFrom(Object obj) {
        return Reflection.getFieldValue(obj, field);
    }

    public void setValueIn(StringKeyMap source, Object target) {
        if (isIgnored) return;
        getValueFrom(source)
          .ifPresent(rawValue -> {
              final Object value = RSObjectMapper.toTargetObject(rawValue, valueType, genericType);
              Reflection.setFieldValue(target, field, value);
          })
          .ifAbsent(() -> Reflection.setFieldValue(target, field, getDefaultValue()));
    }
}
