package ch.zhaw.prometheus.model.rps;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public class RpsRoundCompletesMatchDecision extends Decision {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    protected RpsRoundCompletesMatchDecision() {
    }

    public RpsRoundCompletesMatchDecision(Storage storage) {
        super(new NoOpPolicy());
        this.storage = storage;
    }

    @Override
    public boolean decide(EventHistory events, PolicyRuntime runtime) {
        if (this.storage == null || !this.storage.containsKey(RpsStorageKeys.TARGET_WINS)) {
            return false;
        }
        int targetWins = this.storage.get(RpsStorageKeys.TARGET_WINS).getAsInt();
        if (targetWins < 1) {
            return false;
        }
        RpsSign userSign = latestUserSign(events);
        if (userSign == null) {
            return false;
        }
        RpsRoundOutcome outcome = RpsRules.evaluate(RpsStorageSupport.currentAgentSign(this.storage), userSign);
        RpsMatchScore score = RpsMatchScore.from(this.storage);
        return switch (outcome) {
            case AGENT_WIN -> score.agentWins() + 1 >= targetWins;
            case USER_WIN -> score.userWins() + 1 >= targetWins;
            case DRAW -> false;
        };
    }

    private static RpsSign latestUserSign(EventHistory events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        List<Event> source = events.toList();
        for (int index = source.size() - 1; index >= 0; index--) {
            Event event = source.get(index);
            if (event == null || !Event.TYPE_HAND_SIGN.equals(event.getType())) {
                continue;
            }
            try {
                JsonElement payload = JsonParser.parseString(event.getPayload());
                if (!payload.isJsonObject() || !payload.getAsJsonObject().has("sign")) {
                    return null;
                }
                return RpsSign.parse(payload.getAsJsonObject().get("sign").getAsString());
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "RpsRoundCompletesMatchDecision";
    }
}
