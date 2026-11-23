package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.task.*;
import fixtures.behaviour.*;

class CoordinationLargeAgreementUnitTest {
    @Test void maximumShapeAgreementsKeepFullTermsWithBoundedNativeNavigation() {
        var proposal=HandSequenceFixture.largeAgreement();
        System.out.println("HI11 large fixture bytes="+CoreTaskVoice.bytes(proposal.toString()));
        var parsed=CoordinationAgreement.parse(proposal);String original=parsed.json().toString();
        assertTrue(CoreTaskVoice.bytes(original)>24000);
        assertTrue(CoreTaskVoice.bytes(parsed.checks().getFirst().description())<=600);
        var f=new CoordinationAgentUnitTest();f.propose(proposal,f.live);
        assertEquals(parsed.json(),f.storage().get(CoordinationMemory.DRAFT));
        assertTrue(CoreTaskVoice.bytes(f.policy().liveActivityContext().facts())<=4000);
        assertTrue(CoordinationMemory.facts(f.storage()).contains(original));
        var evidence=new JsonObject();for(int i=0;i<3;i++)evidence.add(Integer.toString(i),new JsonObject());f.storage().put(CoordinationMemory.EVIDENCE,evidence);
        var response=f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject();response.remove("supportOnly");response.addProperty("proposalReview",true);
        assertTrue(CoreTaskVoice.bytes(f.policy().liveActivityContext().facts())<=4000);
        assertTrue(f.policy().liveActivityContext().facts().contains("cockpit task details"));
        assertTrue(f.policy().liveActivityContext().continuation().facts().contains("Do not invent omitted details"));
        f.start(f.live);assertEquals(parsed.json(),f.storage().get(CoordinationMemory.AGREEMENT));
        assertTrue(CoreTaskVoice.bytes(f.policy().liveActivityContext().facts())<=4000);
        assertTrue(CoreTaskVoice.bytes(CoordinationMemory.voiceFacts(f.storage()))<=2600);
    }
}
