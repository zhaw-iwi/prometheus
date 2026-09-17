package ch.zhaw.prometheus.model.rps;

import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.Entity;

@Entity
public class RpsSelectAgentSignAction extends Action {
    private static final DeterministicRpsSignSelector SELECTOR = new DeterministicRpsSignSelector();

    protected RpsSelectAgentSignAction() {
    }

    public RpsSelectAgentSignAction(Storage storage) {
        super(new NoOpPolicy(), storage, RpsStorageKeys.CURRENT_AGENT_SIGN);
        blocking();
    }

    @Override
    public void execute(EventHistory eventHistory, PolicyRuntime runtime) {
        compute(RpsStorageSupport.completedRoundCount(getStorage())).forEach(
                (key, value) -> getStorage().put(key, com.google.gson.JsonParser.parseString(value)));
    }

    @Override public ch.zhaw.prometheus.model.PreparedAction prepare(EventHistory events,
            ch.zhaw.prometheus.model.snapshot.ObservationSnapshot snapshot, PolicyRuntime runtime) {
        int count = RpsStorageSupport.completedRoundCount(getStorage());
        return prepared(java.util.Set.of(RpsStorageKeys.CURRENT_AGENT_SIGN, RpsStorageKeys.CURRENT_ROUND_NUMBER),
                gateway -> compute(count));
    }

    private static java.util.Map<String, String> compute(int count) {
        return java.util.Map.of(RpsStorageKeys.CURRENT_AGENT_SIGN, new JsonPrimitive(SELECTOR.selectForNextRound(count).canonical()).toString(),
                RpsStorageKeys.CURRENT_ROUND_NUMBER, Integer.toString(count + 1));
    }

    @Override
    public String toString() {
        return "RpsSelectAgentSignAction";
    }
}

