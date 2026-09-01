package ch.zhaw.prometheus.definition.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            assertNotNull(ui.authoringRole(), definition.key().toString());
            assertNotNull(ui.exposure(), definition.key().toString());
            if (ui.exposure() == ComponentAuthoringExposure.GUIDED) {
                assertTrue(ui.capabilityGroup().matches("[a-z][a-z0-9-]*"), definition.key().toString());
                assertNull(ui.advancedReason(), definition.key().toString());
            } else {
                assertNull(ui.capabilityGroup(), definition.key().toString());
                assertFalse(ui.advancedReason().isBlank(), definition.key().toString());
            }
            ComponentEnvelope defaultEnvelope = new ComponentEnvelope(definition.key().kind(),
                    definition.key().version(), ui.defaultConfig().value());
            assertEquals(List.of(), registry.validateConfig(defaultEnvelope), definition.key().toString());
            registry.compile(defaultEnvelope);
            for (ImmutableJson example : ui.examples()) {
                ComponentEnvelope exampleEnvelope = new ComponentEnvelope(definition.key().kind(),
                        definition.key().version(), example.value());
                assertEquals(List.of(), registry.validateConfig(exampleEnvelope), definition.key().toString());
            }
            JsonNode schema = definition.configSchema();
            assertEquals(ui.label(), schema.path("title").asText(), definition.key().toString());
            assertEquals(ui.description(), schema.path("description").asText(), definition.key().toString());
            assertEquals(ui.defaultConfig().value(), schema.path("default"), definition.key().toString());
            assertEquals(ui.examples().size(), schema.path("examples").size(), definition.key().toString());
            schema.path("properties").fields().forEachRemaining(field -> {
                assertFalse(field.getValue().path("title").asText().isBlank(), definition.key().toString());
                assertFalse(field.getValue().path("description").asText().isBlank(), definition.key().toString());
                assertTrue(field.getValue().path("examples").isArray(), definition.key().toString());
            });
            String serialized = ui + schema.toString();
            assertFalse(serialized.matches("(?is).*\\b(?:class(?:name)?|bean(?:name)?|scripts?|sourcecode)\\b.*"),
                    definition.key().toString());
        }
    }

    @Test
    void v2AuthoringMetadataSeparatesGuidedCapabilitiesFromGeneratedAndAdvancedComponents() {
        ComponentRegistry registry = BuiltInComponentCatalog.createRegistry();

        assertAuthoring(registry, "prometheus.policy.prompt", ComponentAuthoringRole.RESPONSE_STRATEGY,
                ComponentAuthoringExposure.GUIDED, "prompt-response");
        ComponentUiMetadata exactText = registry.find("prometheus.policy.exact-text", 1).orElseThrow().uiMetadata();
        assertEquals(ComponentAuthoringRole.RESPONSE_STRATEGY, exactText.authoringRole());
        assertEquals(ComponentAuthoringExposure.ADVANCED, exactText.exposure());
        assertNull(exactText.capabilityGroup());
        assertTrue(exactText.advancedReason().contains("Talk to Me"));
        assertAuthoring(registry, "prometheus.decision.prompt", ComponentAuthoringRole.RULE_CONDITION,
                ComponentAuthoringExposure.GUIDED, "semantic-condition");
        assertAuthoring(registry, "prometheus.action.extract", ComponentAuthoringRole.OUTCOME_EXTRACTION,
                ComponentAuthoringExposure.GUIDED, "outcome-report");
        assertAuthoring(registry, "prometheus.initializer.random-choice", ComponentAuthoringRole.DATA_INITIALIZER,
                ComponentAuthoringExposure.GUIDED, "starting-context");
        assertAuthoring(registry, "prometheus.resource.typed-choices", ComponentAuthoringRole.DATA_RESOURCE,
                ComponentAuthoringExposure.GUIDED, "starting-context");
        assertAuthoring(registry, "prometheus.action.rps-evaluate-round",
                ComponentAuthoringRole.DETERMINISTIC_OPERATION, ComponentAuthoringExposure.GUIDED,
                "rock-scissor-paper");
        assertAuthoring(registry, "prometheus.policy.rps-result",
                ComponentAuthoringRole.DETERMINISTIC_OPERATION, ComponentAuthoringExposure.GUIDED,
                "rock-scissor-paper");

        ComponentUiMetadata generated = registry.find("prometheus.decision.latest-event-type", 1).orElseThrow()
                .uiMetadata();
        assertEquals(ComponentAuthoringRole.RULE_TRIGGER, generated.authoringRole());
        assertEquals(ComponentAuthoringExposure.GENERATED_INTERNAL, generated.exposure());
        assertTrue(generated.advancedReason().contains("event trigger"));

        ComponentUiMetadata advanced = registry.find("prometheus.selector.state-path", 1).orElseThrow()
                .uiMetadata();
        assertEquals(ComponentAuthoringRole.TECHNICAL_SELECTOR, advanced.authoringRole());
        assertEquals(ComponentAuthoringExposure.ADVANCED, advanced.exposure());
        assertTrue(advanced.advancedReason().contains("Advanced"));
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
                new ComponentUiMetadata("Test", "Test", new ImmutableJson(tree("{}")), List.of(),
                        ComponentAuthoringRole.RESPONSE_STRATEGY, ComponentAuthoringExposure.ADVANCED, null,
                        "Test-only component."),
                ignored -> ComponentSemantics.none(), ignored -> new NoOpPolicyComponent());
    }

    private static void assertAuthoring(ComponentRegistry registry, String kind, ComponentAuthoringRole role,
            ComponentAuthoringExposure exposure, String capabilityGroup) {
        ComponentUiMetadata metadata = registry.find(kind, 1).orElseThrow().uiMetadata();
        assertEquals(role, metadata.authoringRole());
        assertEquals(exposure, metadata.exposure());
        assertEquals(capabilityGroup, metadata.capabilityGroup());
        assertNull(metadata.advancedReason());
    }

    private static JsonNode tree(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
