package ch.zhaw.prometheus.agentdefs.study;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.task.TaskMemory;

/** Study-owned observation protocol: camera and synthetic provenance remain distinct from generic RPS signs. */
public final class StudySigns {
    public static final String EVENT="obs.study.sign";
    public static final String GUIDANCE="""
        STUDY SIGN EXTENSION: Negotiate a short fictional, nonclinical check-in with scoped hand-sign answers.
        This is a bounded ordered list of exact yes/no questions, not independent sensor reactions.
        Use optional agreement.activity exactly:
        {"kind":"SIGN_ANSWERS","questions":["Would you like a short activity?","Would you prefer a quiet activity?","Would you like a break afterwards?"],
         "yesSign":"THUMBS_UP","noSign":"THUMBS_DOWN","waitSign":"OPEN_PALM","resume":"EXPLICIT_TEXT","demonstrate":true}.
        Examples are not imposed preferences. Clarify missing questions, mappings and demonstration preference with the human.
        Support 1–8 exact distinct yes/no questions of at most 160 characters. Either thumb may mean YES, the other means NO.
        OPEN_PALM means WAIT, preserving the pending question and count. Only explicit text Resume permits further answers.
        Text Resume is a control, NEVER an answer. Describe the human role as answering with signs and using Resume only to continue.
        Demonstration, if agreed, needs three fresh recognized sign events before acceptance; verbal claims do not count.
        Camera attempts use browser recognition; synthetic fixtures use disclosed simulated signs. The backend binds a fresh sign to one pending question, run and accepted revision.
        Use checks:[], reactions:null and dialogueLimit equal to questions.length+1. Do not duplicate the numeric budget in roles/success.
        Success means ONLY recording an answer to EVERY agreed question, not a clinical outcome.
        Early Stop is cancellation, NEVER an alternative success criterion. Do not include "or stopped" in the success field.
        Keep stopping permission in the goal/control explanation, separate from success. The executor supplies all questions and acknowledgment wording.
        START requires a separate proposal, completed demonstrations if agreed and private prediction. Return reply:{}.
        Do not use PROPOSE_AND_START, STEP, COMPLETE or RESTART. STOP/PAUSE/RESUME remain available.
        A request to swap yes/no mappings uses REPLACE, copies all other fields verbatim and requires new demonstrations if agreed and renewed acceptance.
        On an ongoing mapping revision, ONLY activity.yesSign and activity.noSign may change. Copy goal, humanRole, agentRole,
        success, questions, checks, dialogueLimit, reactions and all other activity values CHARACTER-FOR-CHARACTER from the previous accepted agreement.
        Never rewrite humanRole to explain the new mapping: mapping belongs exclusively in activity.yesSign/noSign, not in the role wording.
        Revision retains the pending question and recorded answers; prior signs are never reinterpreted.
        """;
    private StudySigns() {}
    public static boolean task(CoordinationAgreement agreement) { return agreement!=null && agreement.activity()!=null && "SIGN_ANSWERS".equals(agreement.activity().get("kind").getAsString()); }
    public static JsonObject state(Storage storage) { return storage.containsKey(CoordinationMemory.SIGN_STATE)?storage.get(CoordinationMemory.SIGN_STATE).getAsJsonObject().deepCopy():null; }
    private static JsonObject observations(Storage storage){return storage.containsKey(CoordinationMemory.SIGN_OBSERVATIONS)?storage.get(CoordinationMemory.SIGN_OBSERVATIONS).getAsJsonObject().deepCopy():new JsonObject();}
    public static JsonObject scope(Storage storage) {
        var draft=CoordinationMemory.agreement(storage,CoordinationMemory.DRAFT);
        var scope=new JsonObject();
        if(task(draft) && draft.complete() && SignAnswerContract.parse(draft.activity()).demonstrate() && !CoordinationMemory.ready(storage)) {
            scope.addProperty("mode","DEMONSTRATE");scope.addProperty("revision",TaskMemory.text(storage,TaskMemory.REVISION,"0"));
            scope.add("run",JsonNull.INSTANCE);scope.add("question",JsonNull.INSTANCE);
            var remaining=new JsonArray();for(String sign:List.of("THUMBS_UP","THUMBS_DOWN","OPEN_PALM"))if(!CoordinationMemory.evidence(storage).has("sign."+sign))remaining.add(sign);
            scope.add("remaining",remaining);return scope;
        }
        var value=state(storage);
        if(draft!=null || value==null || value.has("outcome") || !value.has("pending"))return null;
        scope.addProperty("mode","ANSWER");scope.add("revision",value.get("agreementRevision"));scope.add("run",value.get("run"));
        scope.add("question",value.getAsJsonObject("pending").get("id"));return scope;
    }
    public static String start(Storage storage,String priorPhase) {
        var accepted=CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT);var contract=SignAnswerContract.parse(accepted.activity());
        var prior=state(storage);boolean ongoing=prior!=null && !prior.has("outcome") && Set.of("RUNNING","PAUSED").contains(priorPhase);
        var value=ongoing?prior:new JsonObject();
        if(!ongoing) { value.add("answers",new JsonArray());value.add("consumed",new JsonArray());value.addProperty("questions",0); }
        value.addProperty("agreementRevision",Integer.toString(Integer.parseInt(TaskMemory.text(storage,TaskMemory.REVISION,"0"))+1));
        value.addProperty("run",TaskMemory.text(storage,CoordinationMemory.RUN,""));value.addProperty("status","WAITING_FOR_SIGN");
        if(!value.has("pending"))ask(value,contract);
        storage.put(TaskMemory.ACTIONS,value.get("questions"));storage.put(CoordinationMemory.SIGN_STATE,value);
        return (ongoing?"The revised mapping is accepted. Continue the same pending question: ":"")+value.getAsJsonObject("pending").get("words").getAsString();
    }
    private static void ask(JsonObject value,SignAnswerContract contract) {
        int index=value.getAsJsonArray("answers").size();
        var pending=new JsonObject();pending.addProperty("id",UUID.randomUUID().toString());pending.addProperty("words",contract.questions().get(index));
        value.add("pending",pending);value.addProperty("questions",index+1);
    }
    public static String rejection(Storage storage,JsonObject payload,Instant now) {
        try {
            if(StudyVision.enabled(storage)) {
                if(!StudyVision.camera(payload) || payload.get("confidence").getAsDouble()<.65 || payload.get("stabilityFrames").getAsInt()<3)return "wrong_source";
            } else if(!payload.get("simulation").getAsBoolean() || !"controlled_study".equals(payload.get("source").getAsString()))return "wrong_source";
            UUID.fromString(payload.get("observationId").getAsString());
            var seen=observations(storage);
            if(seen.has(payload.get("observationId").getAsString()))return "duplicate_sign";
            if(seen.size()>=256)return "sign_limit";
            if(!ObservationEvidence.fresh(ObservationEvidence.observed(payload),now,15,2))return "stale_sign";
            var scope=scope(storage);if(scope==null)return "no_sign_window";
            for(String field:List.of("mode","revision","run","question"))if(!Objects.equals(scope.get(field),payload.get(field)))return "wrong_"+field;
            if(!SignAnswerContract.SIGNS.contains(payload.get("sign").getAsString()))return "unknown_sign";
            if("DEMONSTRATE".equals(scope.get("mode").getAsString())) {
                Instant boundary=Instant.parse(TaskMemory.text(storage,CoordinationMemory.PREPARED_AFTER,now.toString()));
                if(!ObservationEvidence.observed(payload).isAfter(boundary))return "before_proposal";
                if(CoordinationMemory.evidence(storage).has("sign."+payload.get("sign").getAsString()))return "already_demonstrated";
            } else {
                var value=state(storage);
                for(var consumed:value.getAsJsonArray("consumed"))if(consumed.getAsString().equals(payload.get("observationId").getAsString()))return "duplicate_sign";
                if(!TaskMemory.active(storage))return "paused";
                if(value.getAsJsonArray("consumed").size()>=128)return "sign_limit";
            }
            return null;
        } catch(RuntimeException invalid) { return "invalid_sign"; }
    }
    public static void observe(Storage storage,Event event,Instant now) {
        if(!EVENT.equals(event.getType()) || !Set.of("study-simulation","study-camera").contains(event.getActor()))return;
        var payload=JsonParser.parseString(event.getPayload()).getAsJsonObject();String rejected=rejection(storage,payload,now);
        String before=CoordinationMemory.stage(storage);
        if(rejected!=null) { reply(storage,"That sign was not applied ("+rejected.replace('_',' ')+"). The pending question and answers are unchanged.");CoordinationMemory.record(storage,"SIGN_REJECTED",before,event,now);return; }
        var seen=observations(storage);seen.addProperty(payload.get("observationId").getAsString(),payload.get("ts").getAsString());storage.put(CoordinationMemory.SIGN_OBSERVATIONS,seen);
        String sign=payload.get("sign").getAsString();
        if("DEMONSTRATE".equals(payload.get("mode").getAsString())) {
            var evidence=CoordinationMemory.evidence(storage);evidence.add("sign."+sign,payload.deepCopy());storage.put(CoordinationMemory.EVIDENCE,evidence);
            CoordinationMemory.revise(storage);reply(storage,"Recognized "+sign.replace('_',' ').toLowerCase()+" demonstration recorded. "
                    +(CoordinationMemory.ready(storage)?"All demonstrations are complete. Review the proposal and save your prediction before accepting it.":"Demonstrate the remaining signs before acceptance."));
            CoordinationMemory.record(storage,"SIGN_DEMONSTRATION",before,event,now);return;
        }
        var value=state(storage);value.getAsJsonArray("consumed").add(payload.get("observationId"));
        var contract=SignAnswerContract.parse(CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT).activity());
        String words;
        if(sign.equals("OPEN_PALM")) {
            value.addProperty("status","WAITING");CoordinationMemory.phase(storage,"PAUSED");words="Waiting. The pending question is unchanged; say Resume when you are ready.";
        } else {
            String answer=sign.equals(contract.yesSign())?"YES":"NO";
            var response=payload.deepCopy();response.addProperty("answer",answer);response.add("pending",value.get("pending").deepCopy());value.getAsJsonArray("answers").add(response);value.remove("pending");
            if(value.getAsJsonArray("answers").size()==contract.questions().size()) {
                value.addProperty("status","ENDED");value.addProperty("outcome","ANSWERS_RECORDED");CoordinationMemory.phase(storage,"COMPLETED");
                storage.put(CoordinationMemory.OUTCOME,value.get("outcome"));words="Recorded "+answer.toLowerCase()+". All agreed questions have answers. This fictional activity has ended.";
            } else { ask(value,contract);words="Recorded "+answer.toLowerCase()+". "+value.getAsJsonObject("pending").get("words").getAsString(); }
        }
        storage.put(TaskMemory.ACTIONS,value.get("questions"));storage.put(CoordinationMemory.SIGN_STATE,value);reply(storage,words);
        CoordinationMemory.revise(storage);CoordinationMemory.record(storage,sign.equals("OPEN_PALM")?"SIGN_WAIT":"SIGN_ANSWER",before,event,now);
    }
    public static String resume(Storage storage) {
        var value=state(storage);if(value==null || value.has("outcome") || !value.has("pending"))throw new IllegalArgumentException("No pending sign question");
        value.addProperty("status","WAITING_FOR_SIGN");storage.put(CoordinationMemory.SIGN_STATE,value);
        return "Continuing the same question: "+value.getAsJsonObject("pending").get("words").getAsString();
    }
    public static void control(Storage storage,String operation) {
        var value=state(storage);if(value==null || value.has("outcome"))return;
        if(Set.of("STOP","RETURN_TO_CONVERSATION").contains(operation)) {
            value.addProperty("status","ENDED");value.addProperty("outcome",operation.equals("STOP")?"CANCELLED":"RETURNED_TO_CONVERSATION");value.remove("pending");
        } else if(Set.of("PAUSE","DISCARD","PROPOSE","REPLACE").contains(operation))value.addProperty("status","WAITING");
        else return;
        storage.put(CoordinationMemory.SIGN_STATE,value);
    }
    private static void reply(Storage storage,String words) { storage.put(TaskMemory.REPLY,BehaviourPlan.speechOnly(words).toJsonObject()); }
}
