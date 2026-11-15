package ch.zhaw.prometheus.agentdefs.study;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.task.TaskMemory;

/** Deterministic execution of agreed words; observed counts never establish physical privacy. */
public final class StudyContext {
    public static final String GUIDANCE="""
        STUDY CONTEXT EXTENSION: A fictional medication-routine check-in is a one-question encounter, not independent sensor reactions.
        Negotiate the question, exact YES acknowledgment and brief nonjudgmental NO support statement, and the scene rules.
        Use an optional agreement.activity exactly:
        {"kind":"CONTEXT_CHECKIN","question":"In this fictional scenario, has the medication routine been completed?",
        "yesReply":"Thank you for letting me know.","noReply":"You can consider the reminder in the fictional care plan.",
        "startCount":1,"postponeAt":2,"departure":"END","resume":"EXPLICIT_WHEN_ONE"}.
        Only question, yesReply and noReply are example wording, not imposed preferences; clarify these with the human.
        The numeric scene thresholds, departure and resumption rules are FIXED capabilities of this executor, not negotiable examples.
        Do not offer a different visible-person threshold or automatic resumption. Explain the supported policy and ask whether it is acceptable;
        if it is not, keep negotiating without starting or let the participant end the interaction. Do not promise unsupported alternatives.
        Supported rules: after acceptance, a FRESH one-person observation starts one yes/no question;
        two or more visible people postpone immediately; explicit Resume with a current one-person scene continues;
        departure ends an already begun encounter. Return alone never restarts it. Unknown evidence pauses, not absence.
        Counts do not identify a patient or prove real privacy. The session mode determines camera or simulated observations; use only fictional medication information.
        A generic non-sensitive postponement message is supplied by the backend; it never repeats the medication question.
        question, yesReply, noReply: max 200 characters each. question is one yes/no question; replies are final statements, no follow-up question.
        No diagnosis, dosage instruction, coercion, adherence verification or clinical success claim is supported.
        Use dialogueLimit:2, reactions:null, checks:[]; the typed executor owns the question, answer and context transitions.
        A shorter/neutral support request changes only noReply under PROPOSE; copy every other term verbatim.
        START requires a separate proposal and private prediction; return reply:{} and do not ask the check-in question yourself.
        Do not use PROPOSE_AND_START, STEP, COMPLETE or RESTART for this activity. STOP/PAUSE/RESUME remain available.
        """;
    private StudyContext() {}
    public static boolean task(CoordinationAgreement agreement) { return agreement!=null && agreement.activity()!=null && "CONTEXT_CHECKIN".equals(agreement.activity().get("kind").getAsString()); }
    public static JsonObject state(Storage storage) { return storage.containsKey(CoordinationMemory.CONTEXT_STATE)?storage.get(CoordinationMemory.CONTEXT_STATE).getAsJsonObject().deepCopy():null; }
    public static JsonObject scene(Storage storage) { return storage.containsKey(CoordinationMemory.STUDY_SCENE)?storage.get(CoordinationMemory.STUDY_SCENE).getAsJsonObject().deepCopy():null; }
    public static String status(JsonObject value) { return value==null?"NONE":value.get("status").getAsString(); }
    public static int count(Storage storage) { var scene=scene(storage);return scene==null || (StudyVision.camera(scene) && !StudyVision.fresh(scene,Instant.now())) || scene.get("humanCount").isJsonNull()?-1:scene.get("humanCount").getAsInt(); }
    public static String start(Storage storage,String priorPhase) {
        var previous=state(storage);boolean ongoing=previous!=null && !previous.has("outcome")
                && Set.of("RUNNING","PAUSED").contains(priorPhase);
        var value=ongoing?previous:new JsonObject();
        value.addProperty("status",ongoing&&value.has("pending")?"POSTPONED":"WAITING_FOR_ONE");
        value.addProperty("afterScene",scene(storage)==null?0:scene(storage).get("sequence").getAsLong());
        if(!value.has("actions"))value.addProperty("actions",0);
        storage.put(CoordinationMemory.CONTEXT_STATE,value);storage.put(TaskMemory.ACTIONS,value.get("actions"));
        if("POSTPONED".equals(status(value)))CoordinationMemory.phase(storage,"PAUSED");
        return "The check-in is accepted. Show a fresh scene with one visible person. A postponed question also requires Resume.";
    }
    public static void observe(Storage storage,Event event,Instant now) {
        if(!Set.of("study-simulation","study-camera").contains(event.getActor()))return;
        var incoming=JsonParser.parseString(event.getPayload()).getAsJsonObject();
        if(!incoming.has("simulation") || (!incoming.get("simulation").getAsBoolean() && !StudyVision.camera(incoming)))return;
        long sequence=incoming.get("sequence").getAsLong();var old=scene(storage);
        if(old!=null && sequence<=old.get("sequence").getAsLong())return;
        var observed=incoming.deepCopy();
        // Accepted simulations persist until changed. A stale incoming command is
        // unknown evidence; it cannot establish departure or authorize a question.
        if(!TaskMemory.fresh(event,now))observed.add("humanCount",JsonNull.INSTANCE);
        storage.put(CoordinationMemory.STUDY_SCENE,observed);
        var accepted=CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT);var value=state(storage);
        if(!task(accepted) || value==null || value.has("outcome") || storage.containsKey(CoordinationMemory.DRAFT)
                || !Set.of("RUNNING","PAUSED").contains(TaskMemory.phase(storage)))return;
        String before=CoordinationMemory.stage(storage);int count=count(storage);String words=null;
        boolean gap=old!=null && StudyVision.camera(old) && !StudyVision.fresh(old,now);
        if(!gap && old!=null && old.get("humanCount").equals(observed.get("humanCount")) && !"WAITING_FOR_ONE".equals(status(value)))return;
        if(gap && count==1 && "WAITING_FOR_ANSWER".equals(status(value))) {
            value.addProperty("status","POSTPONED");CoordinationMemory.phase(storage,"PAUSED");
            words="Camera information was interrupted. The pending question is unchanged; say Resume when you are ready to continue.";
        } else if(count==0 && value.has("pending")) {
            value.addProperty("status","ENDED");value.addProperty("outcome",StudyVision.camera(incoming)?"OBSERVED_DEPARTURE":"SIMULATED_DEPARTURE");value.remove("pending");
            CoordinationMemory.phase(storage,"COMPLETED");storage.put(CoordinationMemory.OUTCOME,value.get("outcome"));
            words="The person is no longer detected in view. This encounter has ended; return alone will not restart it.";
        } else if(count<0 || count>=2) {
            value.addProperty("status","POSTPONED");CoordinationMemory.phase(storage,"PAUSED");
            words=count<0?"The scene is unknown. We will wait for current scene information before continuing."
                    :"More than one person is detected in view. We can continue later; say Resume when one person is visible again.";
        } else if(count==1 && "WAITING_FOR_ONE".equals(status(value)) && TaskMemory.active(storage)
                && sequence>value.get("afterScene").getAsLong()) {
            words=ask(storage,value,accepted);
        } else if(count==1 && "POSTPONED".equals(status(value))) words="One person is detected in view. The check-in remains paused until you say Resume.";
        storage.put(CoordinationMemory.CONTEXT_STATE,value);
        if(words!=null)reply(storage,words);
        CoordinationMemory.revise(storage);CoordinationMemory.record(storage,"SCENE",before,event,now);
    }
    private static String ask(Storage storage,JsonObject value,CoordinationAgreement accepted) {
        var question=new JsonObject();question.addProperty("id",UUID.randomUUID().toString());question.addProperty("words",ContextCheckInContract.parse(accepted.activity()).question());
        value.add("pending",question);value.addProperty("status","WAITING_FOR_ANSWER");value.addProperty("actions",1);storage.put(TaskMemory.ACTIONS,new JsonPrimitive(1));
        return question.get("words").getAsString();
    }
    public static boolean canResume(Storage storage) { var value=state(storage);var scene=scene(storage);return value!=null && !value.has("outcome") && count(storage)==1
            && scene!=null && scene.get("sequence").getAsLong()>value.get("afterScene").getAsLong(); }
    public static String resume(Storage storage) {
        var value=state(storage);var accepted=CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT);
        if(!canResume(storage))throw new IllegalArgumentException("A fresh one-person scene is required");
        String words=value.has("pending")?"Continuing the pending question: "+value.getAsJsonObject("pending").get("words").getAsString():ask(storage,value,accepted);
        value.addProperty("status","WAITING_FOR_ANSWER");storage.put(CoordinationMemory.CONTEXT_STATE,value);return words;
    }
    public static void control(Storage storage,String operation) {
        var value=state(storage);
        if(value==null || value.has("outcome"))return;
        if(Set.of("STOP","RETURN_TO_CONVERSATION").contains(operation)) {
            value.addProperty("status","ENDED");
            value.addProperty("outcome",operation.equals("STOP")?"CANCELLED":"RETURNED_TO_CONVERSATION");
            value.remove("pending");
        } else if(Set.of("PAUSE","DISCARD","PROPOSE","REPLACE").contains(operation)) {
            value.addProperty("status","POSTPONED");
        } else return;
        storage.put(CoordinationMemory.CONTEXT_STATE,value);
    }
    public static void answer(Storage storage,String answer,Event event) {
        var value=state(storage);
        if(!Set.of("YES","NO").contains(answer) || !"WAITING_FOR_ANSWER".equals(status(value)) || count(storage)!=1 || !TaskMemory.active(storage)) {
            reply(storage,"The pending question is unchanged. Use yes or no only when the check-in is running with one person currently detected.");return;
        }
        var contract=ContextCheckInContract.parse(CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT).activity());
        value.add("answeredQuestion",value.get("pending").deepCopy());value.remove("pending");value.addProperty("answer",answer);value.addProperty("actions",2);
        value.addProperty("status","ENDED");value.addProperty("outcome","FICTIONAL_"+answer+"_REPORTED");storage.put(CoordinationMemory.CONTEXT_STATE,value);
        storage.put(TaskMemory.ACTIONS,new JsonPrimitive(2));storage.put(CoordinationMemory.OUTCOME,value.get("outcome"));CoordinationMemory.phase(storage,"COMPLETED");
        reply(storage,"YES".equals(answer)?contract.yesReply():contract.noReply());CoordinationMemory.revise(storage);
        CoordinationMemory.record(storage,"CHECKIN_ANSWER","EXECUTION",event,Instant.now());
    }
    static void reply(Storage storage,String text) { storage.put(TaskMemory.REPLY,BehaviourPlan.speechOnly(text).toJsonObject()); }
}
