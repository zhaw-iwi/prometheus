package ch.zhaw.statefulconversation.model.commons.actions;

import ch.zhaw.statefulconversation.model.Action;
import ch.zhaw.statefulconversation.model.State;
import ch.zhaw.statefulconversation.model.EventHistory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public class RemoveLastEventAction extends Action {

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State removeFrom;

    protected RemoveLastEventAction() {

    }

    public RemoveLastEventAction(State removeFrom) {
        super(null); // @TODO: maybe redesign the inheritance hierarchy to avoid this?
        this.removeFrom = removeFrom;
    }

    @Override
    public void execute(EventHistory eventHistory) {
        this.removeFrom.getEventHistory().removeLastUserEvent();
    }

    @Override
    public String toString() {
        return "StaticExtractionAction IS-A " + super.toString();
    }

}
