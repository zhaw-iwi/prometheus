package ch.zhaw.prometheus.definition.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.component.CompiledAction;
import ch.zhaw.prometheus.definition.component.CompiledSelector;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.document.ComponentEnvelope;

class BuiltInRuntimeComponentExecutorUnitTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void selectorsCoverEventFieldsStatePathsAndNestedComposition() throws Exception {
        var registry = BuiltInComponentCatalog.createRegistry();
        RuntimeComponentExecutor executor = new BuiltInRuntimeComponentExecutor(new FakeModel());
        RuntimeEvent event = new RuntimeEvent("obs.one", "user", "observation", "payload", List.of("root", "main"));

        assertTrue(executor.selects(selector(registry, "prometheus.selector.any", "{}"), event, "main"));
        assertTrue(executor.selects(selector(registry, "prometheus.selector.state-path", "{}"), event, "main"));
        assertFalse(executor.selects(selector(registry, "prometheus.selector.state-path", "{}"), event, "other"));
        assertTrue(executor.selects(selector(registry, "prometheus.selector.event-type", "{\"types\":[\"obs.one\"]}"), event, "main"));
        assertTrue(executor.selects(selector(registry, "prometheus.selector.actor", "{\"actors\":[\"user\"]}"), event, "main"));
        assertTrue(executor.selects(selector(registry, "prometheus.selector.event-kind", "{\"kinds\":[\"observation\"]}"), event, "main"));
        assertTrue(executor.selects(selector(registry, "prometheus.selector.state-id", "{\"stateIds\":[\"root\"]}"), event, "main"));
        assertTrue(executor.selects(selector(registry, "prometheus.selector.all", """
                {"selectors":[
                  {"kind":"prometheus.selector.event-type","version":1,"config":{"types":["obs.one"]}},
                  {"kind":"prometheus.selector.actor","version":1,"config":{"actors":["user"]}}]}
                """), event, "main"));
    }

    @Test
    void extractionWritesOnlyThroughDeclaredRuntimeStorageBoundary() throws Exception {
        var registry = BuiltInComponentCatalog.createRegistry();
        FakeModel model = new FakeModel();
        RuntimeComponentExecutor executor = new BuiltInRuntimeComponentExecutor(model);
        CompiledAction extraction = (CompiledAction) registry.compile(new ComponentEnvelope(
                "prometheus.action.extract", 1, JSON.readTree("""
                        {"targetStorageKey":"outcome","extractionPrompt":{"sections":[
                          {"id":"extract","kind":"objective","content":"Extract outcome."}]}}
                        """)));
        Map<String, JsonNode> storage = new LinkedHashMap<>();
        RuntimeStorage boundary = new RuntimeStorage() {
            @Override public JsonNode get(String key) { return storage.get(key); }
            @Override public void put(String key, JsonNode value) { storage.put(key, value.deepCopy()); }
            @Override public void remove(String key) { storage.remove(key); }
        };

        executor.execute(extraction, new RuntimeInvocation("main", List.of("main"), List.of(), Map.of()), boundary);

        assertEquals("value", storage.get("outcome").asText());
        assertEquals("Extract outcome.", model.lastExtractionPrompt);
    }

    @Test
    void promptBehaviourActionResolvesOnlyExplicitStorageBindings() throws Exception {
        var registry = BuiltInComponentCatalog.createRegistry();
        FakeModel model = new FakeModel();
        model.generatedBehaviour = RuntimeBehaviour.speechOnly("goodbye");
        RuntimeComponentExecutor executor = new BuiltInRuntimeComponentExecutor(model);
        CompiledAction prompt = (CompiledAction) registry.compile(new ComponentEnvelope(
                "prometheus.action.prompt-behaviour", 1, JSON.readTree("""
                        {
                          "responsePrompt":{"sections":[
                            {"id":"complete","kind":"completion","content":"Use ${therapyAppointmentContext}."}]},
                          "starterPrompt":{"sections":[
                            {"id":"goodbye","kind":"starter","content":"Close ${therapyAppointmentContext}."}]},
                          "storageBindings":[{"key":"therapyAppointmentContext","access":"read",
                            "expectedValueSchema":{"type":"object"}}],
                          "consumedObservations":["obs.user_utterance"],
                          "emittedModalities":["speech"]
                        }
                        """)));
        Map<String, ImmutableJson> storage = Map.of(
                "therapyAppointmentContext", new ImmutableJson(JSON.readTree("{\"label\":\"physiotherapy\"}")),
                "unboundSecret", new ImmutableJson(JSON.readTree("\"must not leak\"")));

        RuntimeBehaviour behaviour = executor.execute(prompt,
                new RuntimeInvocation("main", List.of("main"), List.of(), storage), noOpStorage());

        assertEquals("goodbye", behaviour.speech());
        assertEquals("Use {\"label\":\"physiotherapy\"}.", model.lastGeneratedPrompts.responsePrompt());
        assertEquals("Close {\"label\":\"physiotherapy\"}.", model.lastGeneratedPrompts.starterPrompt());
        assertEquals(List.of("therapyAppointmentContext"), model.lastInvocation.storage().keySet().stream().toList());
        assertFalse(model.lastGeneratedPrompts.responsePrompt().contains("must not leak"));
    }

    private static RuntimeStorage noOpStorage() {
        return new RuntimeStorage() {
            @Override public JsonNode get(String key) { return null; }
            @Override public void put(String key, JsonNode value) { }
            @Override public void remove(String key) { }
        };
    }

    private static CompiledSelector selector(ch.zhaw.prometheus.definition.component.ComponentRegistry registry,
            String kind, String config) throws Exception {
        return (CompiledSelector) registry.compile(new ComponentEnvelope(kind, 1, JSON.readTree(config)));
    }

    private static final class FakeModel implements RuntimeModelGateway {
        private String lastExtractionPrompt;
        private RuntimeBehaviour generatedBehaviour;
        private RuntimePromptBundle lastGeneratedPrompts;
        private RuntimeInvocation lastInvocation;

        @Override
        public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
            this.lastGeneratedPrompts = prompts;
            this.lastInvocation = invocation;
            return this.generatedBehaviour;
        }

        @Override
        public boolean decide(String prompt, RuntimeInvocation invocation) {
            return false;
        }

        @Override
        public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
            this.lastExtractionPrompt = prompt;
            return JsonNodeFactory.instance.textNode("value");
        }
    }
}
