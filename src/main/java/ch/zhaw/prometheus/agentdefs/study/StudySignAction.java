package ch.zhaw.prometheus.agentdefs.study;

import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.TaskMemory;
import jakarta.persistence.Entity;

@Entity
public class StudySignAction extends Action {
    protected StudySignAction() {}
    public StudySignAction(Storage storage) { super(new NoOpPolicy(),storage,TaskMemory.REPLY);blocking(); }
    @Override public void execute(EventHistory events,PolicyRuntime runtime) {
        if(!events.isEmpty() && runtime.taskContinuation().getAsBoolean())StudySigns.observe(getStorage(),events.toList().getLast(),java.time.Instant.now());
    }
}
