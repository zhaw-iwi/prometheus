package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.util.Set;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.*;

@Entity
public class CoreRpsRoundDecision extends Decision {
    public enum Condition { OPEN, SIGN, EXPIRED, ENDED }
    @Enumerated(EnumType.STRING) private Condition rpsCondition;
    protected CoreRpsRoundDecision() {}
    public CoreRpsRoundDecision(Storage storage, Condition condition) {
        super(new CoreRpsActivityPolicy(storage)); rpsCondition = condition;
    }
    @Override public boolean decide(EventHistory events, PolicyRuntime runtime) {
        if (events.isEmpty()) return false;
        var event = events.toList().getLast(); var storage = ((CoreRpsActivityPolicy)getPolicy()).storage(); var now = Instant.now();
        return switch (rpsCondition) {
            case OPEN -> CoreRpsRound.PLAYED.equals(event.getType()) && CoreRpsRound.canOpen(storage, event.getPayload(), runtime.externalSpeech(), now);
            case SIGN -> {
                if (!Event.TYPE_HAND_SIGN.equals(event.getType())) yield false;
                String rejection = CoreRpsRound.rejection(storage, events, runtime.externalSpeech(), now);
                if (rejection != null) ch.zhaw.prometheus.logging.ActivityTrace.cue(rejection, event.getId(), Event.TYPE_HAND_SIGN);
                yield rejection == null;
            }
            case EXPIRED -> CoreRpsRound.expired(storage, now);
            case ENDED -> CoreRpsRound.ENDED.equals(event.getType()) && Set.of("INTRO", "SPOKEN", "WAITING").contains(CoreRpsRound.phase(storage));
        };
    }
}
