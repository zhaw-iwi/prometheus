package ch.zhaw.prometheus.model.commons.actions;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.State;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public class TransferEventHistoryAction extends Action {

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State to;

    protected TransferEventHistoryAction() {

    }

    public TransferEventHistoryAction(State to) {
        super(new NoOpPolicy());
        this.to = to;
    }

    @Override
    public void execute(EventHistory eventHistory) {
        this.to.getSharedEventHistory().append(eventHistory, this.to);
    }

    @Override
    public String toString() {
        return "TransferEventHistoryAction IS-A " + super.toString();
    }
}
