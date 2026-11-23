package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.task.*;
import fixtures.behaviour.HandSequenceFixture;

class CoordinationSequenceUnitTest {
    Event capture(String id,int count,Instant at,int ordinal){var p=HandSequenceFixture.pose(id,count,false,at);p.addProperty("captureIndex",ordinal);return Event.observation(Event.TYPE_HAND_POSE,"camera",p.toString());}
    void frames(CoordinationAgentUnitTest f,String prefix,Instant start){
        for(int i=0;i<6;i++)f.agent.acknowledge(capture(prefix+i,1+i/2,start.plusMillis(i*300),i+1),f.text);
    }
    @Test void preparationAndAcceptedExecutionHaveSeparateSequencesAndOneFinalContribution(){
        var f=new CoordinationAgentUnitTest();var proposal=HandSequenceFixture.agreement(HandSequenceFixture.counts("right",1,2,3));
        assertEquals(proposal,CoordinationAgreement.parse(proposal).json());f.propose(proposal,f.text);assertEquals("PREPARATION",CoordinationMemory.stage(f.storage()));
        f.storage().put(CoordinationMemory.PREPARED_AFTER,new JsonPrimitive(Instant.now().minusSeconds(3).toString()));clearInvocations(f.gateway);
        frames(f,"prepare",Instant.now().minusSeconds(2));verifyNoInteractions(f.gateway);assertEquals("READY",CoordinationMemory.stage(f.storage()));
        f.turn("START",null,"Start now",f.text);assertEquals(0,f.storage().get(TaskMemory.ACTIONS).getAsInt());assertTrue(HandSequenceMemory.progress(f.storage()).isEmpty());
        f.storage().put(TaskMemory.AFTER,new JsonPrimitive(Instant.now().minusSeconds(3).toString()));when(f.gateway.infer(any())).thenReturn("{\"speech\":\"Sequence complete.\"}");clearInvocations(f.gateway);
        var start=Instant.now().minusSeconds(2);
        for(int i=0;i<5;i++){f.agent.acknowledge(capture("run"+i,1+i/2,start.plusMillis(i*300),i+1),f.text);assertEquals(0,f.storage().get(TaskMemory.ACTIONS).getAsInt());verifyNoInteractions(f.gateway);}
        var last=capture("run5",3,start.plusMillis(1500),6);f.agent.acknowledge(last,f.text);assertEquals(1,f.storage().get(TaskMemory.ACTIONS).getAsInt());verify(f.gateway,times(1)).infer(any());
        f.agent.acknowledge(last,f.text);assertEquals(1,f.storage().get(TaskMemory.ACTIONS).getAsInt());assertEquals(proposal,f.storage().get(CoordinationMemory.AGREEMENT));
    }
    @Test void unsupportedSavedProfilesAndForgedCompletionCannotArmOrFire(){
        var f=new CoordinationAgentUnitTest();var proposal=HandSequenceFixture.agreement(HandSequenceFixture.counts("right",1,2,3));f.storage().put(HandFeatures.INPUT_SEQUENCES,new JsonPrimitive(false));f.propose(proposal,f.text);assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        f.storage().put(HandFeatures.INPUT_SEQUENCES,new JsonPrimitive(true));f.propose(proposal,f.text);assertEquals("PREPARATION",CoordinationMemory.stage(f.storage()));
        f.agent.acknowledge(Event.observation(HandSequence.RULE_TYPE,"camera","{\"complete\":true,\"ts\":\""+Instant.now()+"\"}"),f.text);assertEquals("PREPARATION",CoordinationMemory.stage(f.storage()));assertTrue(CoordinationMemory.evidence(f.storage()).isEmpty());
    }
    @Test void sourceChangeRequiresExplicitPreparationRestartAndResumeClearsOnlyRunProgress(){
        var f=new CoordinationAgentUnitTest();var proposal=HandSequenceFixture.agreement(HandSequenceFixture.counts("right",1,2,3));f.propose(proposal,f.text);
        f.storage().put(CoordinationMemory.PREPARED_AFTER,new JsonPrimitive(Instant.now().minusSeconds(3).toString()));var start=Instant.now().minusSeconds(2);
        f.agent.acknowledge(capture("first",1,start,1),f.text);f.agent.acknowledge(capture("second",1,start.plusMillis(300),2),f.text);
        var changed=HandSequenceFixture.pose("new-camera",2,false,start.plusMillis(600));changed.getAsJsonObject("frame").addProperty("revision",2);
        f.agent.acknowledge(Event.observation(Event.TYPE_HAND_POSE,"camera",changed.toString()),f.text);
        assertEquals("restart_required",HandSequenceMemory.progress(f.storage()).getAsJsonObject("prep-0").get("status").getAsString());
        clearInvocations(f.gateway);f.say("Restart preparation",f.text);verifyNoInteractions(f.gateway);assertTrue(HandSequenceMemory.progress(f.storage()).isEmpty());assertEquals(proposal,f.storage().get(CoordinationMemory.DRAFT));
        f.storage().put(CoordinationMemory.PREPARED_AFTER,new JsonPrimitive(Instant.now().minusSeconds(3).toString()));frames(f,"restart",Instant.now().minusSeconds(2));assertEquals("READY",CoordinationMemory.stage(f.storage()));f.start(f.text);
        f.openCueWindow();f.agent.acknowledge(capture("run",1,Instant.now().minusMillis(100),1),f.text);assertFalse(HandSequenceMemory.progress(f.storage()).isEmpty());f.say("Pause",f.text);assertEquals("PAUSED",TaskMemory.phase(f.storage()));
        f.say("Resume",f.text);assertTrue(HandSequenceMemory.progress(f.storage()).isEmpty());assertEquals(0,f.storage().get(TaskMemory.ACTIONS).getAsInt());f.say("Stop",f.text);assertEquals("COMPLETED",TaskMemory.phase(f.storage()));
    }
}
