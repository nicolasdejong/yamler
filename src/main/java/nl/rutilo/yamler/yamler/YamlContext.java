package nl.rutilo.yamler.yamler;

import lombok.RequiredArgsConstructor;
import nl.rutilo.yamler.utils.Value;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
class YamlContext {
    private final Map<String,Object> refs = new HashMap<>();

    public void reset() {
        refs.clear();
    }

    public void storeRef(String ref, Object value) { refs.put(ref, value); }
    public Value<Object> getRef(String ref) {
        return Value.of(ref)
                    .filter(refs::containsKey)
                    .map(refs::get);
    }

}
