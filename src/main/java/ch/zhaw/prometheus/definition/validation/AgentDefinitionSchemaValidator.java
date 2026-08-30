package ch.zhaw.prometheus.definition.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;

public final class AgentDefinitionSchemaValidator {
    public static final String SCHEMA_CLASSPATH = "/agent-definitions/schema/agent-definition.schema.json";

    private final Schema schema;

    public AgentDefinitionSchemaValidator() {
        this.schema = loadSchema();
    }

    public List<SchemaViolation> validate(JsonNode document) {
        return this.schema.validate(document).stream()
                .map(AgentDefinitionSchemaValidator::toViolation)
                .sorted(Comparator.comparing(SchemaViolation::pointer)
                        .thenComparing(SchemaViolation::keyword)
                        .thenComparing(SchemaViolation::message))
                .toList();
    }

    public void requireValid(JsonNode document) {
        List<SchemaViolation> violations = validate(document);
        if (!violations.isEmpty()) {
            throw new AgentDefinitionSchemaException(violations);
        }
    }

    private static Schema loadSchema() {
        try (InputStream input = AgentDefinitionSchemaValidator.class.getResourceAsStream(SCHEMA_CLASSPATH)) {
            if (input == null) {
                throw new IllegalStateException("Agent definition schema is missing from " + SCHEMA_CLASSPATH);
            }
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            return registry.getSchema(input, InputFormat.JSON);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the agent definition schema", exception);
        }
    }

    private static SchemaViolation toViolation(Error error) {
        return new SchemaViolation(toJsonPointer(error.getInstanceLocation()), error.getKeyword(), error.getMessage());
    }

    private static String toJsonPointer(NodePath path) {
        StringBuilder pointer = new StringBuilder();
        for (int index = 0; index < path.getNameCount(); index++) {
            Object segment = path.getElement(index);
            pointer.append('/').append(escapePointerSegment(String.valueOf(segment)));
        }
        return pointer.toString();
    }

    private static String escapePointerSegment(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
