package ch.zhaw.prometheus.model.rps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.Entity;

@Entity
public class RpsEvaluateRoundAction extends Action {
    protected RpsEvaluateRoundAction() {
    }

    public RpsEvaluateRoundAction(Storage storage) {
        super(new NoOpPolicy(), storage, RpsStorageKeys.LAST_ROUND);
    }

    @Override
    public void execute(EventHistory eventHistory, PolicyRuntime runtime) {
        RpsSign agentSign = RpsStorageSupport.currentAgentSign(this.getStorage());
        JsonObject handPayload = latestHandSignPayload(eventHistory);
        RpsSign userSign = RpsSign.parse(requiredString(handPayload, "sign"));
        RpsRoundOutcome outcome = RpsRules.evaluate(agentSign, userSign);

        JsonObject round = new JsonObject();
        round.addProperty("round", RpsStorageSupport.currentRoundNumber(this.getStorage()));
        round.addProperty("agentSign", agentSign.canonical());
        round.addProperty("userSign", userSign.canonical());
        round.addProperty("outcome", outcome.name().toLowerCase());
        round.addProperty("winner", outcome.winner());
        round.addProperty("reason", reason(agentSign, userSign, outcome));
        copyIfPresent(handPayload, round, "confidence", "userConfidence");
        copyIfPresent(handPayload, round, "detectionMode", "userDetectionMode");
        copyIfPresent(handPayload, round, "hand", "userHand");

        JsonArray rounds = RpsStorageSupport.rounds(this.getStorage());
        rounds.add(round);
        this.getStorage().put(RpsStorageKeys.LAST_ROUND, round);
        this.getStorage().put(RpsStorageKeys.ROUNDS, rounds);
    }

    private static JsonObject latestHandSignPayload(EventHistory eventHistory) {
        if (eventHistory == null || eventHistory.isEmpty()) {
            throw new IllegalArgumentException("no hand sign event available");
        }
        java.util.List<Event> events = eventHistory.toList();
        for (int i = events.size() - 1; i >= 0; i--) {
            Event event = events.get(i);
            if (event == null || !Event.TYPE_HAND_SIGN.equals(event.getType())) {
                continue;
            }
            try {
                JsonElement parsed = JsonParser.parseString(event.getPayload());
                if (parsed == null || !parsed.isJsonObject()) {
                    break;
                }
                return parsed.getAsJsonObject();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("invalid hand sign payload", exception);
            }
        }
        throw new IllegalArgumentException("no hand sign event available");
    }

    private static String requiredString(JsonObject payload, String key) {
        if (payload == null || key == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            throw new IllegalArgumentException("hand sign payload is missing " + key);
        }
        return payload.get(key).getAsString();
    }

    private static String reason(RpsSign agentSign, RpsSign userSign, RpsRoundOutcome outcome) {
        return switch (outcome) {
            case DRAW -> agentSign.germanLabel() + " gegen " + userSign.germanLabel();
            case AGENT_WIN -> RpsRules.reason(agentSign, userSign);
            case USER_WIN -> RpsRules.reason(userSign, agentSign);
        };
    }

    private static void copyIfPresent(JsonObject source, JsonObject target, String sourceKey, String targetKey) {
        if (source != null && source.has(sourceKey) && !source.get(sourceKey).isJsonNull()) {
            target.add(targetKey, source.get(sourceKey).deepCopy());
        }
    }

    @Override
    public String toString() {
        return "RpsEvaluateRoundAction";
    }
}

