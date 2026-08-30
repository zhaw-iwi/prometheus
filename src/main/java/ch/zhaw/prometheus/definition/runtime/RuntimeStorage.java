package ch.zhaw.prometheus.definition.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public interface RuntimeStorage {
    JsonNode get(String key);

    void put(String key, JsonNode value);

    void remove(String key);
}
