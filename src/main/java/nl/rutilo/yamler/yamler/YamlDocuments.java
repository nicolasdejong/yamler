package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.collections.StringKeyMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class YamlDocuments extends ArrayList<Object> {

    public static YamlDocuments of(Object... values) {
        final YamlDocuments docs = new YamlDocuments();
        docs.addAll(Arrays.asList(values));
        return docs;
    }

    /**
     * Returns first value of document, which may be a map but can also be a string, number or list
     */
    public Object first() {
        return isEmpty() ? null : get(0);
    }

    /**
     * Returns single value of document or list of documents if there are more than one, or null if none
     */
    public Object value() {
        return size() > 1 ? this : first();
    }

    /**
     * Returns all documents as maps. Single values (like just string, number or list) are returned as { "value": value }
     */
    public List<StringKeyMap> maps() {
        return stream()
            .map(YamlDocuments::convertToStringKeyMap)
            .collect(Collectors.toList());
    }

    /**
     * Returns first document that is a map or an empty map if no such document exists. Ignores non-map documents
     */
    public Map<?,?> firstMap() { // NOSONAR wildcards are inevitable here
        return stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .findFirst()
            .orElseGet(Collections::emptyMap);
    }

    /**
     * Returns first document that is a map or an empty map if no such document exists. Ignores non-map documents
     */
    public StringKeyMap firstStringKeyMap() {
        return stream()
            .filter(Map.class::isInstance)
            .map(YamlDocuments::convertToStringKeyMap)
            .findFirst()
            .orElseGet(StringKeyMap::new);
    }

    private static StringKeyMap convertToStringKeyMap(Object obj) {
        if (obj instanceof StringKeyMap) return (StringKeyMap) obj;
        if (obj instanceof Map) return StringKeyMap.from((Map<?,?>) obj);
        if (obj == null) return new StringKeyMap();
        return new StringKeyMap(Map.of("value", obj));
    }
}
