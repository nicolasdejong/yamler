package nl.rutilo.yamler.objectmapper;

import lombok.EqualsAndHashCode;
import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Reflection;
import nl.rutilo.yamler.utils.StringUtils;
import nl.rutilo.yamler.utils.Value;

import java.lang.reflect.Method;

@EqualsAndHashCode(callSuper = true)
class KVInfoGetterSetter extends KVInfo {
    public final Method getMethod;
    public final Method setMethod;

    public KVInfoGetterSetter(Method getMethod, Method setMethod) {
        super(getMethod.getDeclaringClass(), getMethod.getReturnType(), getMethod.getGenericReturnType(),
            StringUtils.lcFirst(getMethod.getName().substring("get".length())),
            Reflection.getAnnotationsOn(getMethod, setMethod, Reflection.getParameter(setMethod, 0).orElse(null)));
        this.getMethod = getMethod;
        this.setMethod = setMethod;
    }

    public Value<Object> getValueFrom(Object obj) {
        return Reflection.invoke(obj, getMethod);
    }

    public void setValueIn(StringKeyMap source, Object target) {
        if (isIgnored) return;
        getValueFrom(source)
          .ifPresent(rawValue -> Reflection.invoke(target, setMethod, RSObjectMapper.toTargetObject(rawValue, valueType, genericType)))
          .ifAbsent(()        -> Reflection.invoke(target, setMethod, getDefaultValue()));
    }
}
