package nl.rutilo.yamler.objectmapper;

import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.yamler.Yamler;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public abstract class ReflectionStrategy<T> {
    protected final Class<T> clazz;
    protected final List<? extends KVInfo> kvInfos;

    protected ReflectionStrategy(Class<T> clazz, List<? extends KVInfo> fields) {
        this.clazz = Objects.requireNonNull(clazz);
        this.kvInfos = Objects.requireNonNull(fields);
    }

    public abstract T createObjectFrom(StringKeyMap dataMap);

    public StringKeyMap createMapFrom(Object obj) {
        return kvInfos.stream()
            .filter(kvInfo -> kvInfo != null && !kvInfo.isIgnored)
            .map(kvInfo -> new Object[]{kvInfo.name, Yamler.toCollections(kvInfo.getValueFrom(obj)), kvInfo})
            .filter(kv -> kv[1] != null)
            .collect(StringKeyMap::new, (map, kv) -> map.put((String) kv[0], kv[1]), HashMap::putAll)
            ;
    }
}
