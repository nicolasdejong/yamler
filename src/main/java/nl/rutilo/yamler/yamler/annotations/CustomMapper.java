package nl.rutilo.yamler.yamler.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When a class is annotated as CustomConverter it should have:<ul>
 * <li>public static &lt;typeOfClass&gt; fromMap(Map|StringKeyMap)
 * <li>public static Map|StringKeyMap toMap(&lt;typeOfClass&gt;) OR public Map|StringKeyMap toMap()
 * </ul><br>
 * When a converterClass is given, the methods will be found there.<br><p>
 * <p>
 * Ideally the toYamlMap and fromYamlMap would hold method references but that is not supported in Java.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomMapper { // Ideally these would be method references but Java doesn't support that

    /** Requires static method with instance param OR parameterless method on target class */
    String toMap() default "toMap";

    /** Requires static method of this name (which is not possible with interface) */
    String fromMap() default "fromMap";

    /** When another class implements methods. fromMap() must be static then. */
    Class<?> converterClass() default Object.class;
}
