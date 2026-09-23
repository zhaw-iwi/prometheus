package ch.zhaw.prometheus.model.rps;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.Entity;

@Entity
public class RpsTargetWinsDecision extends Decision {
    private boolean expectedValid;

    protected RpsTargetWinsDecision() {
    }

    public RpsTargetWinsDecision(boolean expectedValid) {
        super(new NoOpPolicy());
        this.expectedValid = expectedValid;
    }

    @Override
    public boolean decide(EventHistory events, PolicyRuntime runtime) {
        boolean valid = RpsSetTargetWinsAction.parseTargetWins(
                RpsSetTargetWinsAction.latestUserUtterance(events)).isPresent();
        return valid == this.expectedValid;
    }

    @Override
    public String toString() {
        return "RpsTargetWinsDecision(expectedValid=" + this.expectedValid + ")";
    }
}
