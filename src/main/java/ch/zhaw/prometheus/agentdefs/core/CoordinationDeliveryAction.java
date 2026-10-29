package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import jakarta.persistence.Entity;

@Entity
public class CoordinationDeliveryAction extends Action {
    protected CoordinationDeliveryAction() {}
    public CoordinationDeliveryAction(Storage storage) { super(new NoOpPolicy(), storage, CoordinationDelivery.KEY); blocking(); }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        if (!events.isEmpty()) CoordinationDelivery.apply(getStorage(), events.toList().getLast(), runtime, Instant.now());
    }
}
