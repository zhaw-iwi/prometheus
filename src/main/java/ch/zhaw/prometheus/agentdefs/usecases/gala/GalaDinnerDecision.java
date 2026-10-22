package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.time.Instant;
import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.Entity;

@Entity
public class GalaDinnerDecision extends Decision {
    private String galaCondition;
    protected GalaDinnerDecision() {}
    public GalaDinnerDecision(Storage storage, String condition) {
        super(new GalaDinnerPolicy(storage));
        this.galaCondition = condition;
    }
    @Override public boolean decide(EventHistory events, PolicyRuntime runtime) {
        var storage = ((GalaDinnerPolicy) getPolicy()).storage();
        if (!"EXPIRED".equals(galaCondition)) return galaCondition.equals(GalaDinnerMemory.mode(storage));
        return !events.isEmpty() && Event.TYPE_SYSTEM_TICK.equals(events.toList().getLast().getType())
                && expired(storage, Instant.now());
    }
    static boolean expired(Storage storage, Instant now) {
        if (GalaDinnerMemory.departureDue(storage, now)) return true;
        if (!storage.containsKey(GalaDinnerMemory.LAST_INPUT)) return false;
        return !now.isBefore(GalaDinnerMemory.time(storage, GalaDinnerMemory.LAST_INPUT).plusSeconds(90));
    }
}
