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
    @Transient private String contextPrior;
    protected StudyCoordinationUpdateAction() {}
    public StudyCoordinationUpdateAction(Storage storage,String instructions) { super(storage,instructions+"\n"+StudyGuessing.GUIDANCE+"\n"+StudyContext.GUIDANCE); }
    @Override protected BehaviourPlan interpretationReply(String operation,JsonElement raw) {
        var draft=CoordinationMemory.agreement(getStorage(),CoordinationMemory.DRAFT);
        if(operation.equals("START") && (StudyGuessing.game(draft)||StudyContext.task(draft))) {
            if(raw==null || !raw.isJsonObject() || raw.toString().length()>1800) throw new IllegalArgumentException("Invalid start reply envelope");
            // Interpretation supplies consent only. None of these generated words or
            // optional modalities may bypass the typed executor's first move.
            return BehaviourPlan.speechOnly("Preparing the agreed game question.");
        }
        return super.interpretationReply(operation,raw);
    }
    @Override public void execute(EventHistory events,PolicyRuntime runtime) {
        preparedGame=null; currentEvents=events; contextPrior=null;
        var storage=getStorage(); var accepted=CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT);
        if(!events.isEmpty() && StudyContext.task(accepted) && StudyContext.state(storage)!=null) {
            String text=events.toList().getLast().getPayload(), command=text.toLowerCase(java.util.Locale.ROOT).strip().replaceAll("[.!]+$","");
            boolean closed=StudyContext.state(storage).has("outcome") || "COMPLETED".equals(TaskMemory.phase(storage));
            if(StudyContext.count(storage)!=1 && !command.matches("(please )?(stop|pause|resume)( (the |this )?task)?|return to (general )?conversation")) {
                StudyContext.reply(storage,closed?"This encounter has ended. No private question or answer will be repeated in this scene."
                        :"The check-in is waiting for a one-person simulated scene and explicit resumption. No private question or answer will be repeated here.");return;
            }
            String answer=StudyGuessing.answer(text);if(answer==null && command.equals("not yet"))answer="NO";
            if(!closed && answer!=null && !storage.containsKey(CoordinationMemory.DRAFT)) {
                StudyContext.answer(storage,answer,events.toList().getLast());return;
            }
        }
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
        if(operation.equals("START") && StudyContext.task(draft))contextPrior=TaskMemory.text(storage,CoordinationMemory.PREVIOUS_PHASE,"CONFIGURATION");
        if(StudyContext.task(proposed) && operation.equals("PROPOSE_AND_START"))throw new IllegalArgumentException("A study prediction precedes acceptance");
        if(StudyContext.task(accepted) && Set.of("STEP","COMPLETE","RESTART").contains(operation))throw new IllegalArgumentException("The check-in executor owns contributions and completion");
        if(StudyContext.task(accepted) && operation.equals("RESUME") && !StudyContext.canResume(storage))throw new IllegalArgumentException("A fresh one-person scene is required");
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
        var accepted=CoordinationMemory.agreement(getStorage(),CoordinationMemory.AGREEMENT);
        if(StudyContext.task(accepted)) {
            if(operation.equals("START"))return BehaviourPlan.speechOnly(StudyContext.start(getStorage(),contextPrior));
            if(operation.equals("RESUME"))return BehaviourPlan.speechOnly(StudyContext.resume(getStorage()));
            StudyContext.control(getStorage(),operation);
        }
        return reply;
    }
}
