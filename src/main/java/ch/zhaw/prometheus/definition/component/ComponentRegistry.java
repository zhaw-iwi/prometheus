package ch.zhaw.prometheus.definition.component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.path.NodePath;

import ch.zhaw.prometheus.definition.document.ComponentEnvelope;
import ch.zhaw.prometheus.definition.validation.ComponentSemantics;
import ch.zhaw.prometheus.definition.validation.ComponentSemanticsResolver;

public final class ComponentRegistry implements ComponentSemanticsResolver {
    private final Map<ComponentKey, AgentComponentDefinition> definitions;
    private final Map<ComponentKey, Schema> configSchemas;

    public ComponentRegistry(List<? extends AgentComponentDefinition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("component definitions must not be null");
        }
        Map<ComponentKey, AgentComponentDefinition> indexed = new LinkedHashMap<>();
        Map<ComponentKey, Schema> schemas = new LinkedHashMap<>();
        SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(Dialects.getDraft202012());
        Schema metaSchema = schemaRegistry.getSchema(SchemaLocation.of(Dialects.getDraft202012().getId()));
        for (AgentComponentDefinition definition : definitions) {
            AgentComponentDefinition previous = indexed.putIfAbsent(definition.key(), definition);
            if (previous != null) {
                throw new IllegalStateException("Duplicate component registration for " + definition.key());
            }
            List<Error> schemaErrors = metaSchema.validate(definition.configSchema());
            if (!schemaErrors.isEmpty()) {
                throw new IllegalStateException("Invalid configuration schema for " + definition.key() + ": "
                        + schemaErrors.get(0).getMessage());
            }
            schemas.put(definition.key(), schemaRegistry.getSchema(definition.configSchema()));
        }
        this.definitions = Map.copyOf(indexed);
        this.configSchemas = Map.copyOf(schemas);
    }

    public Optional<AgentComponentDefinition> find(String kind, int version) {
        return Optional.ofNullable(this.definitions.get(new ComponentKey(kind, version)));
    }

    public List<AgentComponentDefinition> definitions() {
        return this.definitions.values().stream()
                .sorted(Comparator.comparing((AgentComponentDefinition definition) -> definition.key().kind())
                        .thenComparingInt(definition -> definition.key().version()))
                .toList();
    }

    public List<ComponentConfigViolation> validateConfig(ComponentEnvelope envelope) {
        ComponentKey key = new ComponentKey(envelope.kind(), envelope.version());
        Schema schema = this.configSchemas.get(key);
        if (schema == null) {
            return List.of();
        }
        List<ComponentConfigViolation> violations = new ArrayList<>();
        for (Error error : schema.validate(envelope.config())) {
            violations.add(new ComponentConfigViolation(toJsonPointer(error.getInstanceLocation()),
                    error.getKeyword(), error.getMessage()));
        }
        return violations.stream()
                .sorted(Comparator.comparing(ComponentConfigViolation::pointer)
                        .thenComparing(ComponentConfigViolation::keyword))
                .toList();
    }

    public CompiledComponent compile(ComponentEnvelope envelope) {
        AgentComponentDefinition definition = find(envelope.kind(), envelope.version())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown component " + envelope.kind() + " version " + envelope.version()));
        return definition.compile(envelope.config(), this);
    }

    @Override
    public ComponentSemantics resolve(ComponentEnvelope envelope) {
        if (!validateConfig(envelope).isEmpty()) {
            return ComponentSemantics.none();
        }
        return find(envelope.kind(), envelope.version())
                .map(definition -> definition.semantics(envelope.config()))
                .orElse(ComponentSemantics.none());
    }

    private static String toJsonPointer(NodePath path) {
        StringBuilder pointer = new StringBuilder();
        for (int index = 0; index < path.getNameCount(); index++) {
            pointer.append('/').append(escape(String.valueOf(path.getElement(index))));
        }
        return pointer.toString();
    }

    private static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
