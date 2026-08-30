package ch.zhaw.prometheus.definition.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.builtin.NoOpPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.CompositeSelectorComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RandomChoiceInitializerComponent;
import ch.zhaw.prometheus.definition.document.ComponentEnvelope;
import ch.zhaw.prometheus.definition.validation.ComponentSemantics;

class ComponentRegistryUnitTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void builtInCatalogExposesVersionedSchemasAndUiMetadata() {
        ComponentRegistry registry = BuiltInComponentCatalog.createRegistry();

        assertTrue(registry.find("prometheus.policy.prompt", 1).isPresent());
        assertTrue(registry.find("prometheus.policy.exact-text", 1).isPresent());
        assertTrue(registry.find("prometheus.policy.rps-reveal", 1).isPresent());
        assertTrue(registry.find("prometheus.action.rps-evaluate-round", 1).isPresent());
        assertTrue(registry.find("prometheus.resource.typed-choices", 1).isPresent());
        assertFalse(registry.find("prometheus.policy.prompt", 2).isPresent());
        assertEquals("Prompt policy", registry.find("prometheus.policy.prompt", 1).orElseThrow()
                .uiMetadata().label());
        assertEquals(23, registry.definitions().size());
    }

    @Test
    void everyPaletteEntryHasSchemaValidDefaultsExamplesAndSafeUiCopy() {
        ComponentRegistry registry = BuiltInComponentCatalog.createRegistry();

        for (AgentComponentDefinition definition : registry.definitions()) {
            ComponentUiMetadata ui = definition.uiMetadata();
            assertFalse(ui.label().isBlank(), definition.key().toString());
            assertFalse(ui.description().isBlank(), definition.key().toString());
            assertFalse(ui.examples().isEmpty(), definition.key().toString());
            ComponentEnvelope defaultEnvelope = new ComponentEnvelope(definition.key().kind(),
                    definition.key().version(), ui.defaultConfig().value());
            assertEquals(List.of(), registry.validateConfig(defaultEnvelope), definition.key().toString());
            registry.compile(defaultEnvelope);
            for (ImmutableJson example : ui.examples()) {
                ComponentEnvelope exampleEnvelope = new ComponentEnvelope(definition.key().kind(),
                        definition.key().version(), example.value());
                assertEquals(List.of(), registry.validateConfig(exampleEnvelope), definition.key().toString());
            }
            String serialized = ui.defaultConfig() + ui.examples().toString();
            assertFalse(serialized.matches("(?is).*\\b(?:class(?:name)?|bean(?:name)?|scripts?|sourcecode)\\b.*"),
                    definition.key().toString());
        }
    }

    @Test
    void duplicateKindAndVersionCannotBeRegistered() {
        AgentComponentDefinition definition = definition("test.policy", tree("{\"type\":\"object\"}"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ComponentRegistry(List.of(definition, definition)));

        assertTrue(failure.getMessage().contains("Duplicate component registration"));
    }

    @Test
    void invalidConfigurationSchemaFailsAtRegistryConstruction() {
        AgentComponentDefinition definition = definition("test.policy", tree("{\"type\":7}"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ComponentRegistry(List.of(definition)));

        assertTrue(failure.getMessage().contains("Invalid configuration schema"));
    }

    @Test
    void registeredSchemaRejectsUnknownConfigurationFields() {
        ComponentRegistry registry = BuiltInComponentCatalog.createRegistry();
        ComponentEnvelope envelope = new ComponentEnvelope("prometheus.policy.no-op", 1,
                tree("{\"unexpected\":true}"));

        assertEquals(1, registry.validateConfig(envelope).size());
        assertEquals("additionalProperties", registry.validateConfig(envelope).getFirst().keyword());
    }

    @Test
    void baselineFactoriesComposePromptsAndUseInjectedRandomnessDeterministically() {
        ComponentRegistry registry = BuiltInComponentCatalog.createRegistry();
        PromptPolicyComponent prompt = (PromptPolicyComponent) registry.compile(new ComponentEnvelope(
                "prometheus.policy.prompt", 1, tree("""
                        {"responsePrompt":{"sections":[
                          {"id":"one","kind":"objective","content":"First\\r\\nline"},
                          {"id":"two","kind":"constraint","content":"Second"}]}}
                        """)));
        RandomChoiceInitializerComponent initializer = (RandomChoiceInitializerComponent) registry.compile(
                new ComponentEnvelope("prometheus.initializer.random-choice", 1,
                        tree("{\"storageKey\":\"choice\",\"choices\":[\"a\",\"b\",\"c\"]}")));

        assertEquals("First\nline\n\nSecond", prompt.responsePrompt());
        assertEquals(initializer.initialize(new Random(19)), initializer.initialize(new Random(19)));
        assertEquals("choice", initializer.targetStorageKey());

        CompositeSelectorComponent nested = (CompositeSelectorComponent) registry.compile(new ComponentEnvelope(
                "prometheus.selector.all", 1, tree("""
                        {"selectors":[
                          {"kind":"prometheus.selector.event-type","version":1,"config":{"types":["obs.one"]}},
                          {"kind":"prometheus.selector.actor","version":1,"config":{"actors":["user"]}}]}
                        """)));
        assertEquals(2, nested.selectors().size());
        assertThrows(UnsupportedOperationException.class, () -> nested.selectors().clear());
    }

    private static AgentComponentDefinition definition(String kind, JsonNode schema) {
        return new RegisteredComponent(new ComponentKey(kind, 1), ComponentCategory.POLICY, schema,
                new ComponentUiMetadata("Test", "Test", new ImmutableJson(tree("{}")), List.of()),
                ignored -> ComponentSemantics.none(), ignored -> new NoOpPolicyComponent());
    }

    private static JsonNode tree(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
