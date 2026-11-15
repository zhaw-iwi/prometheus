package ch.zhaw.prometheus.agentdefs.study;

import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.TaskMemory;
import jakarta.persistence.Entity;

@Entity
public class StudySceneAction extends Action {
    protected StudySceneAction() {}
    public StudySceneAction(Storage storage) { super(new NoOpPolicy(),storage,TaskMemory.REPLY);blocking(); }
    @Override public void execute(EventHistory events,PolicyRuntime runtime) {
        if(events.isEmpty() || !runtime.taskContinuation().getAsBoolean())return;
        var event=events.toList().getLast();
        if(StudyVision.EVENT.equals(event.getType()))getStorage().put(StudyVision.LATEST,com.google.gson.JsonParser.parseString(event.getPayload()));
        StudyContext.observe(getStorage(),event,java.time.Instant.now());
    }
}
