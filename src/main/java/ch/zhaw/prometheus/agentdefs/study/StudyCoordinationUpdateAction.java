package ch.zhaw.prometheus.agentdefs.study;

import java.util.Set;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.core.CoordinationUpdateAction;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.task.TaskMemory;
import jakarta.persistence.*;

/** Study-only executor; shared Coordination still owns all agreement/control transitions. */
@Entity
public class StudyCoordinationUpdateAction extends CoordinationUpdateAction {
    @Transient private JsonObject preparedGame;
    @Transient private EventHistory currentEvents;
    protected StudyCoordinationUpdateAction() {}
    public StudyCoordinationUpdateAction(Storage storage,String instructions) { super(storage,instructions+"\n"+StudyGuessing.GUIDANCE); }
    @Override protected BehaviourPlan interpretationReply(String operation,JsonElement raw) {
        if(operation.equals("START") && StudyGuessing.game(CoordinationMemory.agreement(getStorage(),CoordinationMemory.DRAFT))) {
            if(raw==null || !raw.isJsonObject() || raw.toString().length()>1800) throw new IllegalArgumentException("Invalid start reply envelope");
            // Interpretation supplies consent only. None of these generated words or
            // optional modalities may bypass the typed executor's first move.
            return BehaviourPlan.speechOnly("Preparing the agreed game question.");
        }
        return super.interpretationReply(operation,raw);
    }
    @Override public void execute(EventHistory events,PolicyRuntime runtime) {
        preparedGame=null; currentEvents=events;
        var storage=getStorage(); var accepted=CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT);
        if (!events.isEmpty() && StudyGuessing.game(accepted) && TaskMemory.active(storage) && !storage.containsKey(CoordinationMemory.DRAFT)) {
            String answer=StudyGuessing.answer(events.toList().getLast().getPayload());
            if (answer!=null) {
                try { StudyGuessing.respond(storage,answer,events,runtime); }
                catch (RuntimeException failed) { storage.put(TaskMemory.REPLY,BehaviourPlan.speechOnly(
                        "I could not generate the next game question. Your pending question and budget are unchanged. Please answer it again or stop.").toJsonObject()); }
                return;
            }
        }
        super.execute(events,runtime);
    }
    @Override protected BehaviourPlan prepareActivity(String operation,CoordinationAgreement proposed,BehaviourPlan reply,Event event,PolicyRuntime runtime) {
        var storage=getStorage(); var accepted=CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT);
        var draft=CoordinationMemory.agreement(storage,CoordinationMemory.DRAFT);
        if(operation.equals("REPLACE") && StudyGuessing.game(accepted) && StudyGuessing.game(proposed)
                && event.getPayload().toLowerCase(java.util.Locale.ROOT).matches("(?s).*\\bchange\\b.*\\blimit\\b.*")) {
            var prior=accepted.activity().deepCopy();var revised=proposed.activity().deepCopy();
            prior.remove("questionLimit");revised.remove("questionLimit");
            if(!prior.equals(revised) || !accepted.humanRole().equals(proposed.humanRole()) || !accepted.success().equals(proposed.success()))
                throw new IllegalArgumentException("A limit revision must preserve the other game commitments");
        }
        if (StudyGuessing.game(proposed) && operation.equals("PROPOSE_AND_START")) throw new IllegalArgumentException("A study prediction precedes acceptance");
        if (StudyGuessing.game(accepted) && Set.of("STEP","COMPLETE","RESTART").contains(operation))
            throw new IllegalArgumentException("The game executor owns question and completion decisions");
        if (operation.equals("START") && StudyGuessing.game(draft)) {
            preparedGame=StudyGuessing.start(storage,draft,currentEvents,runtime);
            boolean continuing=preparedGame.equals(StudyGuessing.state(storage));
            return BehaviourPlan.speechOnly((continuing?"The revised terms are active. Continue with the pending question: ":"")+StudyGuessing.wording(preparedGame));
        }
        return reply;
    }
    @Override protected BehaviourPlan applyActivity(String operation,BehaviourPlan reply,Event event,PolicyRuntime runtime) {
        if (operation.equals("START") && preparedGame!=null) { StudyGuessing.apply(getStorage(),preparedGame); return reply; }
        return reply;
    }
}
