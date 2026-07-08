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
    }

    @Override
    public void execute(EventHistory eventHistory, PolicyRuntime runtime) {
        int completedRoundCount = RpsStorageSupport.completedRoundCount(this.getStorage());
        RpsSign sign = SELECTOR.selectForNextRound(completedRoundCount);
        this.getStorage().put(RpsStorageKeys.CURRENT_AGENT_SIGN, new JsonPrimitive(sign.canonical()));
        this.getStorage().put(RpsStorageKeys.CURRENT_ROUND_NUMBER, new JsonPrimitive(completedRoundCount + 1));
    }

    @Override
    public String toString() {
        return "RpsSelectAgentSignAction";
    }
}

