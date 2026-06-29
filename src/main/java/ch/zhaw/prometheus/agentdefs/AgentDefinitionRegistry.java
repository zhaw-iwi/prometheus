package ch.zhaw.prometheus.agentdefs;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class AgentDefinitionRegistry {
    private final Map<String, AgentDefinition> definitions;

    public AgentDefinitionRegistry(List<AgentDefinition> definitions) {
        LinkedHashMap<String, AgentDefinition> indexed = new LinkedHashMap<>();
        List<AgentDefinition> suppliedDefinitions = definitions == null ? List.of() : definitions;
        for (AgentDefinition definition : suppliedDefinitions) {
            if (definition == null) {
                throw new IllegalArgumentException("agent definition must not be null");
            }
        }
        List<AgentDefinition> sortedDefinitions = suppliedDefinitions.stream()
                .sorted(Comparator.comparing(AgentDefinition::key))
                .toList();
        for (AgentDefinition definition : sortedDefinitions) {
            AgentDefinition previous = indexed.putIfAbsent(definition.key(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate agent definition key: " + definition.key());
            }
        }
        this.definitions = Collections.unmodifiableMap(indexed);
    }

    public List<AgentDefinition> list() {
        return List.copyOf(this.definitions.values());
    }

    public Optional<AgentDefinition> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.definitions.get(key));
    }
}
