package ch.zhaw.prometheus.application.study;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ch.zhaw.prometheus.agentdefs.study.StudyCoordinationAgent;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.spi.*;

/** Record at the mutation boundary. Failure aborts the enclosing agent/session commit. */
@Component
public class StudyRecorder {
    public static final String SCHEMA="hri-record-v1";
    private final StudyRecordRepository records;
    private final StudyMaterials materials;
    private final ObjectMapper json;
    private final OpenAIProperties inference;
    private final String sourceRevision;
    private final boolean sourceDirty;
    private final ch.zhaw.prometheus.repositories.AgentRepository agents;
    private final StudyLimits limits;
    private final StudyAdmission admission;
    public StudyRecorder(StudyRecordRepository records,StudyMaterials materials,ObjectMapper json,OpenAIProperties inference,
            @Value("${prometheus.study.source-revision:UNKNOWN}") String revision,
            @Value("${prometheus.study.source-dirty:true}") boolean dirty,ch.zhaw.prometheus.repositories.AgentRepository agents,StudyLimits limits,StudyAdmission admission) {
        this.records=records; this.materials=materials; this.json=json; this.inference=inference;
        sourceRevision=revision; sourceDirty=dirty;
        this.agents=agents;
        this.limits=limits;this.admission=admission;
    }
    public void created(StudyAttempt attempt) {
        var metadata=json.createObjectNode(); metadata.put("schema",SCHEMA);
        metadata.put("sourceRevision",sourceRevision); metadata.put("sourceDirty",sourceDirty);
        metadata.put("language","en"); metadata.put("definition",StudyCoordinationAgent.KEY);
        metadata.put("persona",StudyCoordinationAgent.VOICE);
        metadata.put("foundationFixture",attempt.foundation);
        if(StudySessions.camera(attempt)) {
            var sensing=metadata.putObject("sensing");sensing.put("mode","BROWSER_CAMERA");sensing.put("rawMediaStored",false);
            sensing.put("face","face-api.js@0.22.2/tinyFaceDetector+faceExpressionNet");sensing.put("people","coco-ssd@2.2.3/lite_mobilenet_v2");
            sensing.put("hands","mediapipe-tasks-vision@0.10.35/gesture_recognizer");sensing.put("refreshSeconds",10);sensing.put("freshnessSeconds",15);
            sensing.put("maxVisualReceipts",400);sensing.put("handConfidence",.65);sensing.put("stableHandFrames",3);sensing.put("neutralReleaseFrames",2);
        }
        metadata.set("interactionBounds",json.valueToTree(limits.bounds()));metadata.set("admission",json.valueToTree(admission.status()));
        metadata.put("informationVersion",attempt.informationVersion);metadata.put("informationText",attempt.informationText);
        metadata.set("materials",json.valueToTree(materials.manifest()));
        metadata.put("provider",Objects.toString(inference.getOpenaivsazureopenai(),"unconfigured"));
        var routes=metadata.putObject("configuredRoutes");
        for (InferencePurpose purpose:InferencePurpose.values()) {
            var route=routes.putObject(purpose.name());
            try {
                var configured=InferenceRouting.resolve(inference,purpose);
                route.put("model",configured.model()); route.put("effort",configured.effort());
                route.put("timeoutMs",configured.timeoutMs()); route.put("maxCompletionTokens",configured.maxCompletionTokens());
            } catch (IllegalArgumentException unavailable) { route.put("unconfigured",true); }
        }
        append(attempt.id,"metadata","METADATA",metadata);
    }
    public void procedure(StudyAttempt attempt,String key) {
        var record=json.valueToTree(StudySessions.view(attempt));
        append(attempt.id,"procedure:"+key,"PROCEDURE",record);
    }
    public void provisioned(StudyAttempt attempt,Agent agent) {
        var value=json.createObjectNode(); value.set("events",events(agent,0));
        append(attempt.id,"provision","PROVISION",value);
    }
    public void assessment(StudyAttempt attempt){
        var value=json.createObjectNode();value.put("materialsVersion",attempt.materialsVersion);value.put("access","OPEN_REFERENCE");value.put("finishReason",attempt.finishReason);
        if(attempt.agentId!=null){var storage=agents.findStorageById(attempt.agentId).orElseThrow();
            for(String key:List.of(CoordinationMemory.AGREEMENT,CoordinationMemory.RUN,CoordinationMemory.OUTCOME,TaskMemory.REVISION))
                if(storage.containsKey(key))value.set(key,parse(storage.get(key).toString()));
        }
        append(attempt.id,"assessment","ASSESSMENT_REFERENCE",value);
    }
    public void turn(StudyAttempt attempt,StudyTurn receipt,Agent agent,long sourcePosition) {
        var storage=((TaskPolicy)agent.getCurrentState().ownPolicy()).storage();
        var record=json.createObjectNode(); record.put("requestId",receipt.requestId.toString());
        record.put("inputEventId",receipt.inputEventId.toString()); record.put("turn",attempt.turnVersion);
        record.set("events",events(agent,sourcePosition)); record.put("phase",CoordinationMemory.stage(storage));
        for (String key:List.of(CoordinationMemory.DRAFT,CoordinationMemory.AGREEMENT,CoordinationMemory.ACCEPTED_EVIDENCE,
                CoordinationMemory.RUN,CoordinationMemory.OUTCOME,TaskMemory.REVISION,TaskMemory.ACTIONS,
                ch.zhaw.prometheus.agentdefs.study.StudyGuessing.KEY,CoordinationMemory.CONTEXT_STATE,CoordinationMemory.STUDY_SCENE,CoordinationMemory.SIGN_STATE,ch.zhaw.prometheus.agentdefs.study.StudyVision.LATEST))
            if (storage.containsKey(key)) record.set(key,parse(storage.get(key).toString()));
        var journal=record.putArray("journal");
        if (storage.containsKey(CoordinationMemory.LOG)) for (var entry:storage.get(CoordinationMemory.LOG).getAsJsonArray()) {
            var value=entry.getAsJsonObject();
            if (value.has("sourcePosition") && value.get("sourcePosition").getAsLong()>=sourcePosition)
                journal.add(parse(value.toString()));
        }
        append(attempt.id,"turn:"+receipt.requestId,"TURN",record);
        boolean accepted=false;
        for (var entry:journal) if ("START".equals(entry.path("operation").asText())) accepted=true;
        if (accepted) {
            if (!record.has(CoordinationMemory.AGREEMENT)) throw new IllegalStateException("Acceptance without a saved agreement");
            var snapshot=json.createObjectNode(); snapshot.put("requestId",receipt.requestId.toString());
            snapshot.put("sourceEventId",receipt.inputEventId.toString());
            snapshot.put("ordinal",records.countByAttemptIdAndKind(attempt.id,"ACCEPTANCE")+1);
            snapshot.set("agreement",record.get(CoordinationMemory.AGREEMENT).deepCopy());
            snapshot.set("evidence",record.get(CoordinationMemory.ACCEPTED_EVIDENCE));
            snapshot.set("run",record.get(CoordinationMemory.RUN)); snapshot.set("revision",record.get(TaskMemory.REVISION));
            append(attempt.id,"acceptance:"+receipt.requestId,"ACCEPTANCE",snapshot);
        }
    }
    StudyRecord append(UUID attempt,String key,String kind,JsonNode value) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Research evidence requires a commit transaction");
        return records.saveAndFlush(new StudyRecord(attempt,key,kind,value.toString()));
    }
    JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (java.io.IOException invalid) { throw new IllegalStateException("Invalid research record",invalid); }
    }
    ArrayNode events(Agent agent,long from) {
        var result=json.createArrayNode();
        for (Event event:agent.getEventHistory().toList()) if (event.sourcePosition()!=null && event.sourcePosition()>=from) {
            var item=result.addObject(); item.put("id",event.getId().toString()); item.put("position",event.sourcePosition());
            item.put("type",event.getType()); item.put("actor",event.getActor()); item.put("kind",event.getKind());
            item.put("payload",event.getPayload()); item.put("serverCreatedAt",event.getCreatedDate().toString());
            item.set("statePath",json.valueToTree(event.getStatePath()));
        }
        return result;
    }
}
