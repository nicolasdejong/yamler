package nl.rutilo.yamler.objectmapper;

import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.Reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class KVInfoFieldSetter extends KVInfoField {
    public final Method setter;

    public KVInfoFieldSetter(Field field, Method setter) {
        super(field);
        this.setter = setter;
    }

    @Override
    public void setValueIn(StringKeyMap source, Object target) {
        if (isIgnored) return;
        getValueFrom(source)
          .ifPresent(value -> Reflection.invoke(target, setter, value))
          .ifAbsent(()     -> Reflection.invoke(target, setter, getDefaultValue()));
    }
}
