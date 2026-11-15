package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.study.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.*;
import static ch.zhaw.prometheus.application.study.StudyGuessingTest.update;

class StudySignsTest {
    final LanguageModelGateway gateway=mock(LanguageModelGateway.class);
    final Agent agent=new StudyCoordinationAgent().createAgent();
    final PolicyRuntime runtime=new PolicyRuntime(new PromptMessageAssembler(),gateway);
    Storage storage(){return ((TaskPolicy)agent.getCurrentState().ownPolicy()).storage();}
    static JsonObject contract(boolean demonstrate,boolean swapped){
        var value=JsonParser.parseString("""
          {"goal":"A fictional preference check-in","humanRole":"Answer with the agreed simulated signs","agentRole":"Ask the saved questions in order",
          "success":"The agreed answers are recorded","questions":[],"checks":[],"dialogueLimit":4,"reactions":null,
          "activity":{"kind":"SIGN_ANSWERS","questions":["Would you like a short activity?","Would you prefer a quiet activity?","Would you like a break afterwards?"],
          "yesSign":"THUMBS_UP","noSign":"THUMBS_DOWN","waitSign":"OPEN_PALM","resume":"EXPLICIT_TEXT","demonstrate":true}}
          """).getAsJsonObject();
        value.getAsJsonObject("activity").addProperty("demonstrate",demonstrate);
        if(swapped){value.getAsJsonObject("activity").addProperty("yesSign","THUMBS_DOWN");value.getAsJsonObject("activity").addProperty("noSign","THUMBS_UP");}return value;
    }
    Event say(String text){return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE,"user",text),runtime);}
    JsonObject payload(String sign){var value=StudySigns.scope(storage()).deepCopy();value.remove("remaining");value.addProperty("simulation",true);value.addProperty("source","controlled_study");value.addProperty("sign",sign);value.addProperty("observationId",UUID.randomUUID().toString());value.addProperty("ts",Instant.now().toString());return value;}
    Event observe(JsonObject value){return agent.acknowledge(Event.observation(StudySigns.EVENT,"study-simulation",value.toString()),runtime);}
    void demonstrate(){for(String sign:List.of("THUMBS_UP","THUMBS_DOWN","OPEN_PALM"))observe(payload(sign));}
    void propose(boolean demo){when(gateway.infer(any())).thenReturn(update("PROPOSE",contract(demo,false)));say("Propose this check-in without starting.");}
    void start(){when(gateway.infer(any())).thenReturn(update("START",null));say("Start now");}
    @Test void demonstrationsAreEvidenceOnlyAndCannotAcceptOrAnswer(){
        propose(true);assertEquals("PREPARATION",CoordinationMemory.stage(storage()));start();assertNull(StudySigns.state(storage()));
        say("I demonstrated all three signs");assertEquals("PREPARATION",CoordinationMemory.stage(storage()));
        var up=payload("THUMBS_UP");observe(up);assertNull(StudySigns.state(storage()));assertEquals(1,CoordinationMemory.evidence(storage()).size());
        observe(up);assertEquals(1,CoordinationMemory.evidence(storage()).size());
        observe(payload("THUMBS_DOWN"));observe(payload("OPEN_PALM"));assertEquals("READY",CoordinationMemory.stage(storage()));
        assertNull(StudySigns.scope(storage()));start();assertEquals("EXECUTION",CoordinationMemory.stage(storage()));
        assertEquals(3,storage().get(CoordinationMemory.ACCEPTED_EVIDENCE).getAsJsonObject().size());
        observe(up);assertEquals(0,StudySigns.state(storage()).getAsJsonArray("answers").size());
        var reusedDemo=payload("THUMBS_UP");reusedDemo.add("observationId",up.get("observationId"));observe(reusedDemo);
        assertEquals(0,StudySigns.state(storage()).getAsJsonArray("answers").size());
    }
    @Test void oneFreshSignAnswersOneQuestionAndWaitPreservesIdentity(){
        propose(false);start();var up=payload("THUMBS_UP");observe(up);var next=StudySigns.state(storage()).get("pending");
        assertEquals("YES",StudySigns.state(storage()).getAsJsonArray("answers").get(0).getAsJsonObject().get("answer").getAsString());
        observe(up);assertEquals(next,StudySigns.state(storage()).get("pending"));
        var held=payload("THUMBS_UP");held.add("observationId",up.get("observationId"));observe(held);assertEquals(next,StudySigns.state(storage()).get("pending"));
        var wait=payload("OPEN_PALM");observe(wait);assertEquals("PAUSED",CoordinationMemory.stage(storage()));
        observe(payload("THUMBS_DOWN"));assertEquals(next,StudySigns.state(storage()).get("pending"));say("Resume");observe(wait);
        assertEquals("EXECUTION",CoordinationMemory.stage(storage()));assertEquals(2,storage().get(TaskMemory.ACTIONS).getAsInt());
        observe(payload("THUMBS_DOWN"));observe(payload("THUMBS_UP"));assertEquals("REFLECTION",CoordinationMemory.stage(storage()));
        assertEquals("ANSWERS_RECORDED",storage().get(CoordinationMemory.OUTCOME).getAsString());assertNull(StudySigns.scope(storage()));
    }
    @Test void rejectsWrongScopeUnknownSourceAndStaleSignsWithoutConsumingQuestion(){
        propose(false);start();var before=StudySigns.state(storage());
        for(String field:List.of("run","question","revision","source","sign","ts")){
            var invalid=payload("THUMBS_UP");invalid.addProperty(field,field.equals("ts")?Instant.now().minusSeconds(30).toString():"invalid");
            observe(invalid);assertEquals(before,StudySigns.state(storage()));
        }
        clearInvocations(gateway);say("Yes");verifyNoInteractions(gateway);assertEquals(before,StudySigns.state(storage()));
        agent.acknowledge(Event.observation(StudySigns.EVENT,"foreign",payload("THUMBS_UP").toString()),runtime);assertEquals(before,StudySigns.state(storage()));
        say("Stop");assertEquals("CANCELLED",StudySigns.state(storage()).get("outcome").getAsString());assertNull(StudySigns.scope(storage()));
    }
    @Test void acceptedMappingRevisionPreservesQuestionAndPriorAnswersButFencesOldSigns(){
        propose(true);demonstrate();start();observe(payload("THUMBS_UP"));var old=payload("THUMBS_DOWN");var pending=StudySigns.state(storage()).get("pending");
        when(gateway.infer(any())).thenReturn(update("REPLACE",contract(true,true)));say("Swap yes and no signs, keeping all other commitments.");
        assertEquals("PREPARATION",CoordinationMemory.stage(storage()));observe(old);assertEquals(1,StudySigns.state(storage()).getAsJsonArray("answers").size());
        demonstrate();start();assertEquals(pending,StudySigns.state(storage()).get("pending"));observe(old);
        assertEquals(1,StudySigns.state(storage()).getAsJsonArray("answers").size());observe(payload("THUMBS_DOWN"));
        var answers=StudySigns.state(storage()).getAsJsonArray("answers");assertEquals("YES",answers.get(0).getAsJsonObject().get("answer").getAsString());assertEquals("YES",answers.get(1).getAsJsonObject().get("answer").getAsString());
        var changed=contract(true,true);changed.getAsJsonObject("activity").getAsJsonArray("questions").set(2,new JsonPrimitive("A different question?"));
        when(gateway.infer(any())).thenReturn(update("REPLACE",changed));say("Swap mappings again.");assertFalse(storage().containsKey(CoordinationMemory.DRAFT));
        changed=contract(true,false);changed.addProperty("humanRole","Answer using the newly mapped thumb signs.");
        when(gateway.infer(any())).thenReturn(update("REPLACE",changed));say("Please swap the yes and no mappings, keeping roles unchanged.");
        assertFalse(storage().containsKey(CoordinationMemory.DRAFT));assertEquals(contract(true,true),storage().get(CoordinationMemory.AGREEMENT));
    }
}
