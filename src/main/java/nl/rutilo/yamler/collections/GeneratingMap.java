package nl.rutilo.yamler.collections;

import nl.rutilo.yamler.utils.Value;

import java.util.HashMap;
import java.util.function.Supplier;

/** Map that generates values for requested keys */
public class GeneratingMap<K, V> extends HashMap<K, V> { // NOSONAR -- no need to override equals
    private final transient Supplier<V> valueGenerator;

    public GeneratingMap(Supplier<V> valueGenerator) {
        this.valueGenerator = valueGenerator;
    }

    @Override
    public V get(Object k) {
        if (!containsKey(k) && k != null) {
            //noinspection unchecked
            put((K) k, valueGenerator.get());
        }
        return super.get(k);
    }

    public Value<V> getValue(Object k) {
        return containsKey(k) ? Value.of(get(k)) : Value.absent();
    }
}
