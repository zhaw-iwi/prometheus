package ch.zhaw.prometheus.definition.runtime;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.builtin.RpsEvaluateRoundActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsResultPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsRevealPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsSelectSignActionComponent;
import ch.zhaw.prometheus.model.rps.CoreRpsBehaviour;
import ch.zhaw.prometheus.model.rps.DeterministicRpsSignSelector;
import ch.zhaw.prometheus.model.rps.RpsRoundOutcome;
import ch.zhaw.prometheus.model.rps.RpsRules;
import ch.zhaw.prometheus.model.rps.RpsSign;

/** Trusted deterministic runtime mechanics for the registered English RPS components. */
final class RpsRuntimeSupport {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DeterministicRpsSignSelector SIGN_SELECTOR = new DeterministicRpsSignSelector();

    private RpsRuntimeSupport() {
    }

    static void select(RpsSelectSignActionComponent component, RuntimeStorage storage) {
        ArrayNode rounds = rounds(storage.get(component.roundsStorageKey()));
        RpsSign sign = SIGN_SELECTOR.selectForNextRound(rounds.size());
        storage.put(component.currentAgentSignStorageKey(), JsonNodeFactory.instance.textNode(sign.canonical()));
        storage.put(component.currentRoundNumberStorageKey(), JsonNodeFactory.instance.numberNode(rounds.size() + 1));
    }

    static void evaluate(RpsEvaluateRoundActionComponent component, RuntimeInvocation invocation,
            RuntimeStorage storage) {
        RpsSign agentSign = RpsSign.parse(requiredText(storage.get(component.currentAgentSignStorageKey()),
                component.currentAgentSignStorageKey()));
        int roundNumber = requiredPositiveInteger(storage.get(component.currentRoundNumberStorageKey()),
                component.currentRoundNumberStorageKey());
        ObjectNode handSign = latestHandSign(invocation.history(), component.handSignEventType());
        RpsSign userSign = RpsSign.parse(requiredText(handSign.get("sign"), "sign"));
        RpsRoundOutcome outcome = RpsRules.evaluate(agentSign, userSign);

        ObjectNode round = JsonNodeFactory.instance.objectNode();
        round.put("round", roundNumber);
        round.put("agentSign", agentSign.canonical());
        round.put("userSign", userSign.canonical());
        round.put("outcome", outcome.name().toLowerCase(Locale.ROOT));
        round.put("winner", outcome.winner());
        round.put("reason", storedReason(agentSign, userSign, outcome));
        copyIfPresent(handSign, round, "confidence", "userConfidence");
        copyIfPresent(handSign, round, "detectionMode", "userDetectionMode");
        copyIfPresent(handSign, round, "hand", "userHand");

        ArrayNode rounds = rounds(storage.get(component.roundsStorageKey()));
        rounds.add(round.deepCopy());
        storage.put(component.lastRoundStorageKey(), round);
        storage.put(component.roundsStorageKey(), rounds);
    }

    static RuntimeBehaviour reveal(RpsRevealPolicyComponent component, Map<String, ImmutableJson> storage) {
        RpsSign sign = RpsSign.parse(requiredText(value(storage, component.currentAgentSignStorageKey()),
                component.currentAgentSignStorageKey()));
        int round = requiredPositiveInteger(value(storage, component.currentRoundNumberStorageKey()),
                component.currentRoundNumberStorageKey());
        return runtimeBehaviour(CoreRpsBehaviour.reveal(sign, round));
    }

    static RuntimeBehaviour result(RpsResultPolicyComponent component, Map<String, ImmutableJson> storage) {
        JsonNode value = value(storage, component.lastRoundStorageKey());
        if (value == null || !value.isObject()) {
            throw new IllegalStateException("RPS round result is not available");
        }
        ObjectNode round = (ObjectNode) value;
        return runtimeBehaviour(CoreRpsBehaviour.result(
                RpsSign.parse(requiredText(round.get("agentSign"), "agentSign")),
                RpsSign.parse(requiredText(round.get("userSign"), "userSign")),
                requiredText(round.get("winner"), "winner"),
                requiredPositiveInteger(round.get("round"), "round")));
    }

    private static ObjectNode latestHandSign(List<RuntimeEvent> history, String eventType) {
        for (int index = history.size() - 1; index >= 0; index--) {
            RuntimeEvent event = history.get(index);
            if (!eventType.equals(event.type())) {
                continue;
            }
            try {
                JsonNode parsed = JSON.readTree(event.payload());
                if (parsed != null && parsed.isObject()) {
                    return (ObjectNode) parsed;
                }
                throw new IllegalArgumentException("invalid hand sign payload");
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("invalid hand sign payload", exception);
            }
        }
        throw new IllegalArgumentException("no hand sign event available");
    }

    private static ArrayNode rounds(JsonNode value) {
        return value != null && value.isArray() ? ((ArrayNode) value).deepCopy()
                : JsonNodeFactory.instance.arrayNode();
    }

    private static JsonNode value(Map<String, ImmutableJson> storage, String key) {
        ImmutableJson value = storage.get(key);
        return value == null ? null : value.value();
    }

    private static String requiredText(JsonNode value, String name) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("RPS value is missing or malformed: " + name);
        }
        return value.asText();
    }

    private static int requiredPositiveInteger(JsonNode value, String name) {
        if (value == null || !value.isIntegralNumber() || value.asInt() < 1) {
            throw new IllegalStateException("RPS value is missing or malformed: " + name);
        }
        return value.asInt();
    }

    private static String storedReason(RpsSign agentSign, RpsSign userSign, RpsRoundOutcome outcome) {
        return switch (outcome) {
            case DRAW -> agentSign.germanLabel() + " gegen " + userSign.germanLabel();
            case AGENT_WIN -> RpsRules.reason(agentSign, userSign);
            case USER_WIN -> RpsRules.reason(userSign, agentSign);
        };
    }

    private static RuntimeBehaviour runtimeBehaviour(CoreRpsBehaviour.Plan plan) {
        return new RuntimeBehaviour(plan.speech(), immutable(plan.nonVerbal()), immutable(plan.motion()),
                immutable(plan.display()));
    }

    private static ImmutableJson immutable(Object value) {
        return value == null ? null : new ImmutableJson(JSON.valueToTree(value));
    }

    private static void copyIfPresent(ObjectNode source, ObjectNode target, String sourceKey, String targetKey) {
        JsonNode value = source.get(sourceKey);
        if (value != null && !value.isNull()) {
            target.set(targetKey, value.deepCopy());
        }
    }
}
