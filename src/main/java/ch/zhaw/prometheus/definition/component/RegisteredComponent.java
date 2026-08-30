package ch.zhaw.prometheus.definition.component;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiFunction;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.validation.ComponentSemantics;

public final class RegisteredComponent implements AgentComponentDefinition {
    private final ComponentKey key;
    private final ComponentCategory category;
    private final JsonNode configSchema;
    private final ComponentUiMetadata uiMetadata;
    private final Function<JsonNode, ComponentSemantics> semanticsFactory;
    private final BiFunction<JsonNode, ComponentRegistry, CompiledComponent> compiler;

    public RegisteredComponent(ComponentKey key, ComponentCategory category, JsonNode configSchema,
            ComponentUiMetadata uiMetadata, Function<JsonNode, ComponentSemantics> semanticsFactory,
            Function<JsonNode, CompiledComponent> compiler) {
        this(key, category, configSchema, uiMetadata, semanticsFactory,
                (config, ignoredRegistry) -> compiler.apply(config));
    }

    public RegisteredComponent(ComponentKey key, ComponentCategory category, JsonNode configSchema,
            ComponentUiMetadata uiMetadata, Function<JsonNode, ComponentSemantics> semanticsFactory,
            BiFunction<JsonNode, ComponentRegistry, CompiledComponent> compiler) {
        this.key = Objects.requireNonNull(key, "key");
        this.category = Objects.requireNonNull(category, "category");
        this.configSchema = Objects.requireNonNull(configSchema, "configSchema").deepCopy();
        this.uiMetadata = Objects.requireNonNull(uiMetadata, "uiMetadata");
        this.semanticsFactory = Objects.requireNonNull(semanticsFactory, "semanticsFactory");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    @Override
    public ComponentKey key() {
        return this.key;
    }

    @Override
    public ComponentCategory category() {
        return this.category;
    }

    @Override
    public JsonNode configSchema() {
        return this.configSchema.deepCopy();
    }

    @Override
    public ComponentUiMetadata uiMetadata() {
        return this.uiMetadata;
    }

    @Override
    public ComponentSemantics semantics(JsonNode config) {
        return this.semanticsFactory.apply(config.deepCopy());
    }

    @Override
    public CompiledComponent compile(JsonNode config) {
        return this.compiler.apply(config.deepCopy(), null);
    }

    @Override
    public CompiledComponent compile(JsonNode config, ComponentRegistry registry) {
        return this.compiler.apply(config.deepCopy(), registry);
    }
}
