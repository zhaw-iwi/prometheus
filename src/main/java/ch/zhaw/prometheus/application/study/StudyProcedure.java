package ch.zhaw.prometheus.application.study;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.study.StudyGuessing;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import static ch.zhaw.prometheus.application.study.StudySessions.*;

/** Private predictions gate the procedure; they never supply an agent instruction or answer. */
@Service
public class StudyProcedure {
    public record Activity(String revision,boolean predictionRequired,boolean predictionSaved,String game,
            String revisionInstruction,String probeInstruction) {}
    private final StudySessions sessions;
    private final StudyFormRepository forms;
    private final AgentRepository agents;
    private final StudyRecorder recorder;
    private final StudyMaterials materials;
    public StudyProcedure(StudySessions sessions,StudyFormRepository forms,AgentRepository agents,StudyRecorder recorder,StudyMaterials materials) {
        this.sessions=sessions;this.forms=forms;this.agents=agents;this.recorder=recorder;this.materials=materials;
    }
    Storage storage(StudyAttempt attempt) { return ((TaskPolicy)agents.findById(attempt.agentId).orElseThrow().getCurrentState().ownPolicy()).storage(); }
    static String revision(Storage storage) { return TaskMemory.text(storage,TaskMemory.REVISION,"0"); }
    boolean required(Storage storage) { return CoordinationMemory.ready(storage) && StudyGuessing.game(CoordinationMemory.agreement(storage,CoordinationMemory.DRAFT)); }
    boolean saved(StudyAttempt attempt,Storage storage) { return forms.findByAttemptIdAndKind(attempt.id,"prediction."+revision(storage)).isPresent(); }
    @Transactional(readOnly=true)
    public Activity view(String token) {
        var attempt=sessions.require(token,false); open(attempt);
        if(attempt.agentId==null) return new Activity("0",false,false,null,null,null);
        var storage=storage(attempt); var scenario=materials.scenario(attempt.scenario);
        boolean played=StudyGuessing.game(CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT))
                && !"REFLECTION".equals(CoordinationMemory.stage(storage));
        return new Activity(revision(storage),required(storage),saved(attempt,storage),
                StudyGuessing.state(storage)==null?null:StudyGuessing.state(storage).toString(),
                played?scenario.revision().prompt():null,played?scenario.probe().prompt():null);
    }
    @Transactional
    public Activity predict(String token,Map<String,String> answer) {
        var attempt=sessions.require(token,true); open(attempt);
        if (attempt.agentId==null || attempt.pendingRequest!=null || !Set.of(StudyAttempt.Stage.COORDINATION,StudyAttempt.Stage.REVISION,StudyAttempt.Stage.TRIAL).contains(attempt.stage))
            throw failure(HttpStatus.CONFLICT,"No reviewable proposal");
        var storage=storage(attempt);
        if (answer==null || !answer.keySet().equals(Set.of("revision","prediction")) || answer.get("prediction")==null
                || answer.get("prediction").isBlank() || answer.get("prediction").length()>2000) throw failure(HttpStatus.BAD_REQUEST,"Enter a prediction");
        if (!required(storage) || !revision(storage).equals(answer.get("revision"))) throw failure(HttpStatus.CONFLICT,"Review the current proposal");
        String kind="prediction."+revision(storage), encoded=new Gson().toJson(new TreeMap<>(answer));
        var existing=forms.findByAttemptIdAndKind(attempt.id,kind);
        if(existing.isPresent()) {
            if(!existing.get().answers.equals(encoded)) throw failure(HttpStatus.CONFLICT,"Prediction already submitted");
        } else {
            forms.saveAndFlush(new StudyForm(attempt,kind,encoded)); recorder.procedure(attempt,"form:"+kind);
        }
        return view(token);
    }
    boolean permitsStart(StudyAttempt attempt) {
        var storage=storage(attempt); return !required(storage) || saved(attempt,storage);
    }
}
