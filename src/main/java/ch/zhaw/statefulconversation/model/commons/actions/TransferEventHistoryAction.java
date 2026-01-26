package ch.zhaw.statefulconversation.model.commons.actions;

import ch.zhaw.statefulconversation.model.Action;
import ch.zhaw.statefulconversation.model.State;
import ch.zhaw.statefulconversation.model.EventHistory;
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
        super(null);
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
