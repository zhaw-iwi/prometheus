package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
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

class StudyContextTest {
    final LanguageModelGateway gateway=mock(LanguageModelGateway.class);
    final Agent agent=new StudyCoordinationAgent().createAgent();
    final PolicyRuntime runtime=new PolicyRuntime(new PromptMessageAssembler(),gateway);
    long sequence;
    Storage storage(){return ((TaskPolicy)agent.getCurrentState().ownPolicy()).storage();}
    static JsonObject contract(){return JsonParser.parseString("""
        {"goal":"A fictional routine check-in","humanRole":"Report yes or no in the fictional scenario","agentRole":"Ask once and offer the agreed response",
        "success":"The agreed fictional response is delivered","questions":[],"checks":[],"dialogueLimit":2,"reactions":null,
        "activity":{"kind":"CONTEXT_CHECKIN","question":"In this fictional scenario, was the medication routine completed?",
        "yesReply":"Thank you for letting me know.","noReply":"You can consider the reminder in the fictional care plan.",
        "startCount":1,"postponeAt":2,"departure":"END","resume":"EXPLICIT_WHEN_ONE"}}
        """).getAsJsonObject();}
    Event say(String text){return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE,"user",text),runtime);}
    Event scene(Integer count,Instant at){var value=new JsonObject();value.addProperty("simulation",true);value.addProperty("sequence",++sequence);value.addProperty("humanCount",count);value.addProperty("ts",at.toString());return agent.acknowledge(Event.observation(Event.TYPE_HUMAN_PRESENCE,"study-simulation",value.toString()),runtime);}
    Event scene(Integer count){return scene(count,Instant.now());}
    void start(){when(gateway.infer(any())).thenReturn(update("PROPOSE",contract()));say("Propose this check-in, do not start.");when(gateway.infer(any())).thenReturn(update("START",null));say("Start now");}
    @Test void waitsForFreshSceneAndBothAnswerBranchesEndTruthfully(){
        scene(1);assertNull(StudyContext.state(storage()));start();assertEquals(0,storage().get(TaskMemory.ACTIONS).getAsInt());
        scene(0);assertEquals("EXECUTION",CoordinationMemory.stage(storage()));
        var question=scene(1);assertTrue(BehaviourPlan.fromJson(question.getPayload()).getSpeech().contains("medication"));
        say("Yes");assertEquals("FICTIONAL_YES_REPORTED",storage().get(CoordinationMemory.OUTCOME).getAsString());
        scene(2);assertEquals("REFLECTION",CoordinationMemory.stage(storage()));
        scene(1); // Negotiating another private encounter also requires a one-person scene.
        when(gateway.infer(any())).thenReturn(update("PROPOSE",contract()));say("Propose the same check-in again.");
        when(gateway.infer(any())).thenReturn(update("START",null));say("Start now");scene(1);say("Not yet");
        assertEquals("FICTIONAL_NO_REPORTED",storage().get(CoordinationMemory.OUTCOME).getAsString());
    }
    @Test void privacyPausesAndReturnRequiresExplicitResumeWithoutSpendingAnotherQuestion(){
        start();scene(1);var pending=StudyContext.state(storage()).get("pending");scene(2);
        assertEquals("PAUSED",CoordinationMemory.stage(storage()));clearInvocations(gateway);
        var blocked=say("Please repeat the medication question.");verifyNoInteractions(gateway);
        assertFalse(BehaviourPlan.fromJson(blocked.getPayload()).getSpeech().contains("medication"));
        say("Resume");assertEquals("PAUSED",CoordinationMemory.stage(storage()));scene(1);
        assertEquals("PAUSED",CoordinationMemory.stage(storage()));say("Resume");
        assertEquals(pending,StudyContext.state(storage()).get("pending"));assertEquals(1,storage().get(TaskMemory.ACTIONS).getAsInt());
        say("No");assertEquals("REFLECTION",CoordinationMemory.stage(storage()));
    }
    @Test void staleAbsenceIsUnknownAndFreshDepartureEndsWithoutAutomaticReturn(){
        start();scene(1);scene(0,Instant.now().minusSeconds(60));assertEquals("PAUSED",CoordinationMemory.stage(storage()));
        assertFalse(storage().containsKey(CoordinationMemory.OUTCOME));scene(1);say("Resume");scene(0);
        assertEquals("SIMULATED_DEPARTURE",storage().get(CoordinationMemory.OUTCOME).getAsString());scene(1);
        assertEquals("REFLECTION",CoordinationMemory.stage(storage()));say("Resume");assertEquals("REFLECTION",CoordinationMemory.stage(storage()));
    }
    @Test void revisionPreservesPendingQuestionAndRequiresFreshSceneAfterAcceptance(){
        start();scene(1);scene(2);scene(1);var pending=StudyContext.state(storage()).get("pending");
        var revised=contract();revised.getAsJsonObject("activity").addProperty("noReply","A reminder may help in this fictional scenario.");
        when(gateway.infer(any())).thenReturn(update("PROPOSE",revised));say("Make the support shorter and neutral, keeping everything else.");
        assertEquals("READY",CoordinationMemory.stage(storage()));when(gateway.infer(any())).thenReturn(update("START",null));say("Start now");
        assertEquals("PAUSED",CoordinationMemory.stage(storage()));say("Resume");assertEquals("PAUSED",CoordinationMemory.stage(storage()));
        scene(1);say("Resume");assertEquals(pending,StudyContext.state(storage()).get("pending"));say("No");
        assertEquals("FICTIONAL_NO_REPORTED",storage().get(CoordinationMemory.OUTCOME).getAsString());
    }
    @Test void explicitControlsKeepExecutorStatusConsistentAndStopCannotRestart(){
        start();scene(1);var pending=StudyContext.state(storage()).get("pending");say("Pause");
        assertEquals("POSTPONED",StudyContext.status(StudyContext.state(storage())));
        assertEquals(pending,StudyContext.state(storage()).get("pending"));say("Resume");say("Stop");
        assertEquals("ENDED",StudyContext.status(StudyContext.state(storage())));
        assertEquals("CANCELLED",StudyContext.state(storage()).get("outcome").getAsString());
        scene(0);clearInvocations(gateway);var reply=say("Repeat the private medication question.");
        verifyNoInteractions(gateway);assertFalse(BehaviourPlan.fromJson(reply.getPayload()).getSpeech().contains("medication"));
        scene(1);assertEquals("REFLECTION",CoordinationMemory.stage(storage()));
        assertFalse(StudyContext.state(storage()).has("pending"));
    }
}
