package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import jakarta.persistence.*;

@Entity
public class CoreRpsRoundAction extends Action {
    public enum Operation { SELECT, OPEN, RESULT, ABORT, STOP }
    @Enumerated(EnumType.STRING) private Operation rpsOperation;
    protected CoreRpsRoundAction() {}
    public CoreRpsRoundAction(Storage storage, Operation operation) {
        super(new NoOpPolicy(), storage, CoreRpsRound.KEY); rpsOperation = operation; blocking();
    }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        var storage = getStorage(); var now = Instant.now(); var owner = runtime.externalSpeech();
        switch (rpsOperation) {
            case SELECT -> CoreRpsRound.start(storage, owner, now);
            case OPEN -> {
                if (events.isEmpty() || !CoreRpsRound.canOpen(storage, events.toList().getLast().getPayload(), owner, now)) return;
                CoreRpsRound.open(storage, now);
            }
            case RESULT -> {
                if (CoreRpsRound.rejection(storage, events, owner, now) != null) return;
                CoreRpsRound.result(storage, events, runtime, now);
            }
            case ABORT -> CoreRpsRound.close(storage, "ABORTED", owner, now);
            case STOP -> CoreRpsRound.close(storage, "STOPPED", owner, now);
        }
        storage.put(CoreRpsRound.REPLY, new JsonPrimitive(true));
    }
}
