package ch.zhaw.prometheus.model.behaviour;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.task.*;
import fixtures.behaviour.OutputSequenceFixture;
class OutputSequenceUnitTest {
    final ch.zhaw.prometheus.model.interaction.AgentInteractionProfile profile=new ch.zhaw.prometheus.agentdefs.core.CoordinationAgent().createAgent().getInteractionProfile();
    @Test void silentCountingIsOneDirectCommandOrOneTaskPlanWithAllTenFingers(){
        var sequence=OutputSequenceFixture.counting(1,10,500);var request=DirectCommands.take(OutputSequenceFixture.command(sequence));var history=new EventHistory();history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE,"user","Count one to ten silently"));var storage=new Storage();
        var result=DirectCommands.evaluate(request,profile,storage,history,false);assertEquals("ACCEPTED",result.status());assertNull(result.plan().getSpeech());assertEquals(sequence,result.plan().getMotion().getAsJsonObject().getAsJsonObject("handAction").get("action"));assertEquals(result.plan().toJson(),TaskMemory.plan(result.plan().toJsonObject(),profile).toJson());
        assertEquals("DUPLICATE",DirectCommands.evaluate(request,profile,storage,history,false).status());assertFalse(storage.containsKey(TaskMemory.ACTIONS));assertEquals(1,storage.get(DirectCommands.HISTORY).getAsJsonArray().size());
    }
    @Test void nestedMalformedLongAndUnsupportedSubstepsRejectAsAWhole(){
        var sequence=OutputSequenceFixture.counting(1,5,500);
        for(int hold:new int[]{99,5001}){var invalid=sequence.deepCopy();invalid.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("holdMs",hold);assertThrows(IllegalArgumentException.class,()->HandActions.pose(invalid));}
        var nested=sequence.deepCopy();nested.getAsJsonArray("steps").get(0).getAsJsonObject().add("action",sequence);assertThrows(IllegalArgumentException.class,()->HandActions.pose(nested));
        var longSequence=OutputSequenceFixture.counting(0,10,5000);assertThrows(IllegalArgumentException.class,()->HandActions.pose(longSequence));
        var old=ch.zhaw.prometheus.model.interaction.AgentInteractionProfile.of(List.of(),List.of(HandActions.MODALITY),List.of(HandFeatures.POSES));assertThrows(IllegalArgumentException.class,()->HandActions.requireCapability(OutputSequenceFixture.plan(sequence),old));
    }
}
