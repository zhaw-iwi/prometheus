package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.study.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.*;

class StudyGuessingTest {
    final LanguageModelGateway gateway=mock(LanguageModelGateway.class);
    final Agent agent=new StudyCoordinationAgent().createAgent();
    final PolicyRuntime runtime=new PolicyRuntime(new PromptMessageAssembler(),gateway);
    Storage storage(){return ((TaskPolicy)agent.getCurrentState().ownPolicy()).storage();}
    static JsonObject contract(int limit) {
        return JsonParser.parseString("""
            {"goal":"Guess an everyday object", "humanRole":"Keep the secret and answer", "agentRole":"Ask and guess",
            "success":"The human confirms a specific guess", "questions":[],"checks":[],"reactions":null,"dialogueLimit":%d,
            "activity":{"kind":"GUESSING","secretOwner":"HUMAN","answers":["YES","NO","UNKNOWN"],"questionLimit":%d,"guessesCount":true}}
            """.formatted(limit+1,limit)).getAsJsonObject();
    }
    static String update(String operation,JsonObject terms) {
        var value=new JsonObject();value.addProperty("operation",operation);value.add("agreement",terms==null?JsonNull.INSTANCE:terms);
        value.add("reply",BehaviourPlan.speechOnly("Ready.").toJsonObject());return value.toString();
    }
    static String move(String kind,String words){return "{\"kind\":\""+kind+"\",\"words\":\""+words+"\"}";}
    Event say(String words){return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE,"user",words),runtime);}
    void proposal(int limit){when(gateway.infer(any())).thenReturn(update("PROPOSE",contract(limit)));say("Propose the game, do not start.");}
    void start(String kind,String question){when(gateway.infer(any())).thenReturn(update("START",null),move(kind,question));say("I accept the saved proposal. Start now.");}
    @Test void exactQuestionBudgetAllowsFinalAnswerAndNeverInventsSuccess() {
        proposal(2);assertNull(StudyGuessing.state(storage()));start("QUESTION","Is it used indoors?");
        assertEquals(1,StudyGuessing.state(storage()).get("questions").getAsInt());
        when(gateway.infer(any())).thenReturn(move("QUESTION","Is it made of metal?"));say("No");
        assertEquals("EXECUTION",CoordinationMemory.stage(storage()));assertEquals(2,StudyGuessing.state(storage()).get("questions").getAsInt());
        clearInvocations(gateway);say("Unknown");verifyNoInteractions(gateway);
        assertEquals("REFLECTION",CoordinationMemory.stage(storage()));
        assertEquals("QUESTION_LIMIT_REACHED",storage().get(CoordinationMemory.OUTCOME).getAsString());
    }
    @Test void guessNeedsHumanConfirmationAndCountsAsAQuestion() {
        proposal(1);start("GUESS","Is it a kettle?");
        assertEquals("EXECUTION",CoordinationMemory.stage(storage()));say("Yes");
        assertEquals("HUMAN_CONFIRMED_GUESS",storage().get(CoordinationMemory.OUTCOME).getAsString());
        assertEquals(1,StudyGuessing.state(storage()).get("questions").getAsInt());
    }
    @Test void wrongGuessExhaustionAndPauseStopAreDistinct() {
        proposal(1);start("GUESS","Is it a kettle?");say("Pause");
        var pending=StudyGuessing.state(storage());when(gateway.infer(any())).thenReturn(update("KEEP",null));say("Yes");
        assertEquals(pending,StudyGuessing.state(storage()));assertEquals("PAUSED",CoordinationMemory.stage(storage()));
        say("Resume");say("No");assertEquals("QUESTION_LIMIT_REACHED",storage().get(CoordinationMemory.OUTCOME).getAsString());
        when(gateway.infer(any())).thenReturn(update("REPLACE",contract(2)));say("Replace it with a new game with limit two.");
        start("QUESTION","Is it used indoors?");say("Stop");
        assertEquals("CANCELLED",storage().get(CoordinationMemory.OUTCOME).getAsString());
    }
    @Test void revisionPreservesPendingQuestionRequiresAcceptanceAndProtectsTerms() {
        proposal(5);start("QUESTION","Is it used indoors?");var pending=StudyGuessing.state(storage());
        when(gateway.infer(any())).thenReturn(update("PROPOSE",contract(3)));say("Make the wording shorter");
        assertFalse(storage().containsKey(CoordinationMemory.DRAFT));
        var unintended=contract(3);unintended.getAsJsonObject("activity").add("answers",JsonParser.parseString("[\"YES\",\"NO\"]"));
        when(gateway.infer(any())).thenReturn(update("REPLACE",unintended));say("Change the question limit to three.");
        assertFalse(storage().containsKey(CoordinationMemory.DRAFT));
        when(gateway.infer(any())).thenReturn(update("REPLACE",contract(3)));say("Change the question limit to three, keeping the other terms.");
        assertEquals("READY",CoordinationMemory.stage(storage()));assertEquals(pending,StudyGuessing.state(storage()));
        clearInvocations(gateway);when(gateway.infer(any())).thenReturn(update("START",null));say("I accept the saved proposal. Start now.");
        assertEquals(pending,StudyGuessing.state(storage()));verify(gateway,times(1)).infer(any());
        assertEquals(3,CoordinationMemory.agreement(storage(),CoordinationMemory.AGREEMENT).activity().get("questionLimit").getAsInt());
    }
    @Test void invalidMoveAndUnpermittedAnswerPreserveGameAuthority() {
        var terms=contract(2);terms.getAsJsonObject("activity").add("answers",JsonParser.parseString("[\"YES\",\"NO\"]"));
        when(gateway.infer(any())).thenReturn(update("PROPOSE",terms));say("Propose my game");start("QUESTION","Is it used indoors?");
        var before=StudyGuessing.state(storage());say("Unknown");assertEquals(before,StudyGuessing.state(storage()));
        when(gateway.infer(any())).thenReturn(move("QUESTION","Is it red? Is it big?"));say("No");
        assertEquals(before,StudyGuessing.state(storage()));assertEquals("EXECUTION",CoordinationMemory.stage(storage()));
        when(gateway.infer(any())).thenReturn(update("COMPLETE",null));say("You probably won");
        assertEquals("EXECUTION",CoordinationMemory.stage(storage()));
    }
    @Test void invalidContractsAndUnacceptedImmediatePlayFailClosed() {
        for(String field:List.of("secretOwner","guessesCount","questionLimit")) {
            var value=contract(3);value.getAsJsonObject("activity").addProperty(field,"unsupported");
            assertThrows(IllegalArgumentException.class,()->CoordinationAgreement.parse(value));
        }
        when(gateway.infer(any())).thenReturn(update("PROPOSE_AND_START",contract(3)));say("Start this game immediately");
        assertNull(StudyGuessing.state(storage()));assertFalse(storage().containsKey(CoordinationMemory.AGREEMENT));
    }
    @Test void typedStartUsesOnlyExecutorWordsAndResetClearsItsState() {
        proposal(2);
        when(gateway.infer(any())).thenReturn("{\"operation\":\"START\",\"agreement\":null,\"reply\":{\"speech\":\"\"}}",move("QUESTION","Is it used indoors?"));
        var event=say("Start now");assertEquals("Is it used indoors?",BehaviourPlan.fromJson(event.getPayload()).getSpeech());
        ((ch.zhaw.prometheus.agentdefs.core.CoordinationPolicy)agent.getCurrentState().ownPolicy()).reset();
        assertNull(StudyGuessing.state(storage()));
    }
}
