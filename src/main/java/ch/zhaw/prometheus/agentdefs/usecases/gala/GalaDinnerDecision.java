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
        if ("INTRO_EXPIRED".equals(galaCondition)) return "RPS_INTRO".equals(GalaDinnerMemory.mode(storage))
                && !events.isEmpty() && Event.TYPE_SYSTEM_TICK.equals(events.toList().getLast().getType())
                && !Instant.now().isBefore(GalaDinnerMemory.time(storage, GalaDinnerRounds.CUE_AT).plusSeconds(30));
        if (!"EXPIRED".equals(galaCondition)) return galaCondition.equals(GalaDinnerMemory.mode(storage));
        return !events.isEmpty() && Event.TYPE_SYSTEM_TICK.equals(events.toList().getLast().getType())
                && expired(storage, Instant.now());
    }
    static boolean expired(Storage storage, Instant now) {
        if (!storage.containsKey(GalaDinnerMemory.LAST_INPUT)) return false;
        return !now.isBefore(GalaDinnerMemory.time(storage, GalaDinnerMemory.LAST_INPUT).plusSeconds(90));
    }
}
