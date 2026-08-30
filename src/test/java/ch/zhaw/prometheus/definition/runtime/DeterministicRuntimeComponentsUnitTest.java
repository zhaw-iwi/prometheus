package ch.zhaw.prometheus.definition.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.agentdefs.core.CoreRpsResultPolicy;
import ch.zhaw.prometheus.agentdefs.core.CoreRpsRevealPolicy;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.component.CompiledAction;
import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.document.ComponentEnvelope;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;

class DeterministicRuntimeComponentsUnitTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SELECT_CONFIG = """
            {"roundsStorageKey":"rps_rounds","currentAgentSignStorageKey":"rps_current_agent_sign",
             "currentRoundNumberStorageKey":"rps_current_round_number"}
            """;
    private static final String EVALUATE_CONFIG = """
            {"handSignEventType":"obs.hand.sign","currentAgentSignStorageKey":"rps_current_agent_sign",
             "currentRoundNumberStorageKey":"rps_current_round_number","lastRoundStorageKey":"rps_last_round",
             "roundsStorageKey":"rps_rounds"}
            """;
    private static final String REVEAL_CONFIG = """
            {"currentAgentSignStorageKey":"rps_current_agent_sign",
             "currentRoundNumberStorageKey":"rps_current_round_number"}
            """;
    private static final String RESULT_CONFIG = "{\"lastRoundStorageKey\":\"rps_last_round\"}";

    @Test
    void exactTextPreservesPayloadFiltersEnvelopeAndNeverCallsModel() throws Exception {
        RuntimeComponentExecutor executor = executor();
        CompiledPolicy policy = policy("prometheus.policy.exact-text", """
                {"eventType":"obs.user_utterance","actor":"user","eventKind":"observation",
                 "maxTextCodePoints":2000}
                """);
        String text = "Grüezi, \"Zürich\"!\nLine two 🌍";
        RuntimeInvocation invocation = invocation(List.of(
                new RuntimeEvent("obs.user_utterance", "assistant", "observation", "wrong actor"),
                new RuntimeEvent("obs.user_utterance", "user", "observation", text)));

        RuntimeBehaviour behaviour = executor.start(List.of(policy), invocation);

        assertEquals(text, behaviour.speech());
        assertNull(behaviour.nonVerbal());
        assertNull(executor.generate(List.of(policy), invocation(List.of(
                new RuntimeEvent("obs.user_utterance", "user", "observation", "  \n")))));
        assertNull(executor.generate(List.of(policy), invocation(List.of(
                new RuntimeEvent("obs.user_utterance", "user", "observation", "x".repeat(2001))))));
        assertEquals("🌍".repeat(2000), executor.generate(List.of(policy), invocation(List.of(
                new RuntimeEvent("obs.user_utterance", "user", "observation", "🌍".repeat(2000)))))
                .speech());
    }

    @Test
    void rpsComponentsPreserveSelectionEvaluationRevealResultAndRepetition() throws Exception {
        RuntimeComponentExecutor executor = executor();
        CompiledAction select = action("prometheus.action.rps-select-sign", SELECT_CONFIG);
        CompiledAction evaluate = action("prometheus.action.rps-evaluate-round", EVALUATE_CONFIG);
        CompiledPolicy reveal = policy("prometheus.policy.rps-reveal", REVEAL_CONFIG);
        CompiledPolicy result = policy("prometheus.policy.rps-result", RESULT_CONFIG);
        MapRuntimeStorage storage = new MapRuntimeStorage();

        executor.execute(select, invocation(List.of()), storage);
        assertEquals("rock", storage.get(RpsStorageKeys.CURRENT_AGENT_SIGN).asText());
        assertEquals(1, storage.get(RpsStorageKeys.CURRENT_ROUND_NUMBER).asInt());

        RuntimeBehaviour revealBehaviour = executor.start(List.of(reveal), invocation(List.of(), storage.snapshot()));
        assertRevealParity(revealBehaviour, "rock", 1);

        RuntimeEvent handSign = new RuntimeEvent("obs.hand.sign", "sensor", "observation",
                "{\"sign\":\"scissor\",\"confidence\":0.93,\"detectionMode\":\"camera\",\"hand\":\"right\"}");
        executor.execute(evaluate, invocation(List.of(handSign), storage.snapshot()), storage);
        JsonNode round = storage.get(RpsStorageKeys.LAST_ROUND);
        assertEquals("agent_win", round.path("outcome").asText());
        assertEquals("agent", round.path("winner").asText());
        assertEquals(0.93, round.path("userConfidence").asDouble());
        assertEquals(1, storage.get(RpsStorageKeys.ROUNDS).size());

        RuntimeBehaviour resultBehaviour = executor.start(List.of(result), invocation(List.of(), storage.snapshot()));
        assertResultParity(resultBehaviour, round);

        executor.execute(select, invocation(List.of(), storage.snapshot()), storage);
        assertEquals("scissor", storage.get(RpsStorageKeys.CURRENT_AGENT_SIGN).asText());
        assertEquals(2, storage.get(RpsStorageKeys.CURRENT_ROUND_NUMBER).asInt());
    }

    @Test
    void rpsResultCoversUserWinAndDrawAndMalformedHandSignsFailLoudly() throws Exception {
        RuntimeComponentExecutor executor = executor();
        CompiledPolicy result = policy("prometheus.policy.rps-result", RESULT_CONFIG);
        assertEquals("You win: paper beats rock. My digital agent dignity is lightly dented. Again?",
                executor.start(List.of(result), resultInvocation("rock", "paper", "user", 2)).speech());
        assertEquals("A draw: we both showed scissor. Very synchronized, suspiciously professional. Again?",
                executor.start(List.of(result), resultInvocation("scissor", "scissor", "draw", 3)).speech());

        MapRuntimeStorage storage = new MapRuntimeStorage();
        storage.put(RpsStorageKeys.CURRENT_AGENT_SIGN, JsonNodeFactory.instance.textNode("rock"));
        storage.put(RpsStorageKeys.CURRENT_ROUND_NUMBER, JsonNodeFactory.instance.numberNode(1));
        CompiledAction evaluate = action("prometheus.action.rps-evaluate-round", EVALUATE_CONFIG);
        assertThrows(IllegalArgumentException.class, () -> executor.execute(evaluate,
                invocation(List.of(new RuntimeEvent("obs.hand.sign", "sensor", "observation", "not-json")),
                        storage.snapshot()), storage));
    }

    private static RuntimeComponentExecutor executor() {
        return new BuiltInRuntimeComponentExecutor(new RuntimeModelGateway() {
            @Override
            public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
                throw new AssertionError("deterministic components must not call a model");
            }

            @Override
            public boolean decide(String prompt, RuntimeInvocation invocation) {
                throw new AssertionError("deterministic components must not call a model");
            }

            @Override
            public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
                throw new AssertionError("deterministic components must not call a model");
            }
        });
    }

    private static CompiledPolicy policy(String kind, String config) throws Exception {
        return (CompiledPolicy) BuiltInComponentCatalog.createRegistry()
                .compile(new ComponentEnvelope(kind, 1, JSON.readTree(config)));
    }

    private static CompiledAction action(String kind, String config) throws Exception {
        return (CompiledAction) BuiltInComponentCatalog.createRegistry()
                .compile(new ComponentEnvelope(kind, 1, JSON.readTree(config)));
    }

    private static RuntimeInvocation resultInvocation(String agentSign, String userSign, String winner, int round)
            throws Exception {
        String outcome = "draw".equals(winner) ? "draw" : winner + "_win";
        JsonNode value = JSON.readTree("""
                {"round":%d,"agentSign":"%s","userSign":"%s","outcome":"%s","winner":"%s","reason":"test"}
                """.formatted(round, agentSign, userSign, outcome, winner));
        return invocation(List.of(), Map.of(RpsStorageKeys.LAST_ROUND, new ImmutableJson(value)));
    }

    private static RuntimeInvocation invocation(List<RuntimeEvent> events) {
        return invocation(events, Map.of());
    }

    private static RuntimeInvocation invocation(List<RuntimeEvent> events, Map<String, ImmutableJson> storage) {
        return new RuntimeInvocation("state", List.of("state"), events, storage);
    }

    private static void assertRevealParity(RuntimeBehaviour actual, String sign, int round) throws Exception {
        Storage storage = new Storage();
        storage.put(RpsStorageKeys.CURRENT_AGENT_SIGN, new JsonPrimitive(sign));
        storage.put(RpsStorageKeys.CURRENT_ROUND_NUMBER, new JsonPrimitive(round));
        BehaviourPlan expected = new CoreRpsRevealPolicy(storage).onStart(null, null, null, null);
        assertBehaviourParity(expected, actual);
    }

    private static void assertResultParity(RuntimeBehaviour actual, JsonNode round) throws Exception {
        Storage storage = new Storage();
        storage.put(RpsStorageKeys.LAST_ROUND, JsonParser.parseString(round.toString()));
        BehaviourPlan expected = new CoreRpsResultPolicy(storage).onStart(null, null, null, null);
        assertBehaviourParity(expected, actual);
    }

    private static void assertBehaviourParity(BehaviourPlan expected, RuntimeBehaviour actual) throws Exception {
        assertEquals(expected.getSpeech(), actual.speech());
        assertEquals(node(expected.getNonVerbal()), value(actual.nonVerbal()));
        assertEquals(node(expected.getMotion()), value(actual.motion()));
        assertEquals(node(expected.getDisplay()), value(actual.display()));
    }

    private static JsonNode node(com.google.gson.JsonElement value) throws Exception {
        return value == null ? null : JSON.readTree(value.toString());
    }

    private static JsonNode value(ImmutableJson value) {
        return value == null ? null : value.value();
    }

    private static final class MapRuntimeStorage implements RuntimeStorage {
        private final Map<String, JsonNode> values = new LinkedHashMap<>();

        @Override
        public JsonNode get(String key) {
            JsonNode value = this.values.get(key);
            return value == null ? null : value.deepCopy();
        }

        @Override
        public void put(String key, JsonNode value) {
            this.values.put(key, value.deepCopy());
        }

        @Override
        public void remove(String key) {
            this.values.remove(key);
        }

        private Map<String, ImmutableJson> snapshot() {
            Map<String, ImmutableJson> snapshot = new LinkedHashMap<>();
            this.values.forEach((key, value) -> snapshot.put(key, new ImmutableJson(value)));
            return Map.copyOf(snapshot);
        }
    }
}
