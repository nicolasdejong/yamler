package nl.rutilo.yamler.objectmapper;

import lombok.EqualsAndHashCode;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

@EqualsAndHashCode(callSuper = true)
class KVInfoParamField extends KVInfoField {
    public final Parameter parameter;

    public KVInfoParamField(Parameter param, Field field) {
        super(field, param.getAnnotations());
        this.parameter = param;
    }
}
