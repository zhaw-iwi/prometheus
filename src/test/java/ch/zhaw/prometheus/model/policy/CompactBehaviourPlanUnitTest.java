package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.spi.InferencePurpose;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.NoOpLanguageModelGateway;

class CompactBehaviourPlanUnitTest {
    private static final String CANONICAL = """
            {"speech":"What small step feels manageable?","nonVerbal":{"gesture":"NONE","facialExpression":{"type":"gentleSmile","intensity":0.3},"gaze":{"direction":"toward_user","focus":"older_adult"},"motion":{"stillness":0.9,"energy":0.1}}}
            """.strip();
    private static final String COMPACT = """
            {"speech":"What small step feels manageable?","nv":{"g":"NONE","f":["gentleSmile",0.3],"z":["toward_user","older_adult"],"m":[0.9,0.1]}}
            """.strip();

    @Test void shorterHealthcareOutputPreservesEveryCanonicalValue() {
        assertEquals(JsonParser.parseString(CANONICAL), BehaviourPlanInference.parse(COMPACT).toJsonObject());
        assertEquals(232, CANONICAL.length());
        assertEquals(136, COMPACT.length());
    }

    @Test void customPartialExtendedAndNullModalitiesRemainCanonical() {
        String extras = """
                {"facialExpression":{"type":"thoughtful","customIntensity":"soft"},
                 "gaze":{"direction":"forward"},"motion":null,
                 "posture":{"type":"open","lean":"neutral","openness":0.8},
                 "prosody":{"rate":"slow","pitch":"warm","volume":"soft"},
                 "proxemics":{"distance":"social"},"custom":{"f":["literal",2],"g":true}}
                """;
        String compact = "{\"speech\":\"Hello.\",\"nv\":{\"g\":\"POLITE\",\"x\":" + extras
                + "},\"motion\":{\"handSign\":\"scissors\",\"energy\":0.2,\"move\":\"forward\"},\"display\":{\"mode\":\"banner\",\"text\":\"Hello\"}}";
        var expected = JsonParser.parseString(compact).getAsJsonObject();
        var expectedNonverbal = JsonParser.parseString(extras).getAsJsonObject();
        expectedNonverbal.addProperty("gesture", "POLITE");
        expected.remove("nv");
        expected.add("nonVerbal", expectedNonverbal);
        assertEquals(BehaviourPlanInference.parse(expected.toString()).toJsonObject(),
                BehaviourPlanInference.parse(compact).toJsonObject());
        assertEquals("scissor", BehaviourPlanInference.parse(compact).getMotion().getAsJsonObject().get("handSign").getAsString());
    }

    @Test void numericBoundariesStringContentsAndMissingGestureArePreserved() {
        var raw = JsonParser.parseString("""
                {"speech":"placeholder","nv":{"f":["attentive",0],"z":["forward","user"],"m":[1,0]}}
                """).getAsJsonObject();
        String speech = "  Grüezi! \"Yes\" — مرحباً.\nTwo  spaces.  ";
        raw.addProperty("speech", speech);
        var plan = BehaviourPlanInference.parse(raw.toString());
        assertEquals(speech, plan.getSpeech());
        assertEquals("NONE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals(1, plan.getNonVerbal().getAsJsonObject().getAsJsonObject("motion").get("stillness").getAsInt());
        assertEquals(0, plan.getNonVerbal().getAsJsonObject().getAsJsonObject("facialExpression").get("intensity").getAsInt());
        assertNull(plan.getMotion());
        assertNull(plan.getDisplay());
    }

    @Test void generationPublishesOnlyCanonicalPlanAndHistoryUsesExactSpeech() {
        var policy = policy();
        var gateway = new Gateway(COMPACT);
        var agent = new Agent("synthetic", "compact fixture", new State("s", policy, List.of()));
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
        Event event = agent.start(runtime);
        assertEquals(1, gateway.calls);
        assertEquals(InferencePurpose.BEHAVIOUR, gateway.request.purpose());
        assertEquals(InferenceRequest.Output.JSON_OBJECT, gateway.request.output());
        assertEquals(JsonParser.parseString(CANONICAL), JsonParser.parseString(event.getPayload()));
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, event.getType());
        assertEquals(event.getPayload(), agent.getEventHistory().toList().getLast().getPayload());
        assertEquals("What small step feels manageable?", new BehaviourPlanPromptEventContentAdapter().toPromptContent(event));
        assertEquals(JsonParser.parseString(CANONICAL), BehaviourPlan.fromJson(event.getPayload()).toJsonObject());
        String instruction = gateway.request.messages().getLast().getContent();
        assertTrue(instruction.contains("custom nonverbal instructions"));
        assertTrue(instruction.endsWith(CompactBehaviourPlan.INSTRUCTIONS));
        assertTrue(instruction.contains("f: [type,intensity]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "[]", "{\"unexpected\":1}", "{\"x\":null}", "{\"x\":[]}",
            "{\"f\":null}", "{\"f\":{\"type\":\"warm\",\"intensity\":0.5}}", "{\"f\":[]}",
            "{\"f\":[\"warm\"]}", "{\"f\":[\"warm\",0.5,1]}", "{\"f\":[4,0.5]}",
            "{\"f\":[\"warm\",\"0.5\"]}", "{\"f\":[\"warm\",null]}", "{\"f\":[\"warm\",true]}",
            "{\"f\":[\"warm\",-0.1]}", "{\"f\":[\"warm\",1.1]}", "{\"f\":[\"warm\",1e999]}",
            "{\"z\":[\"forward\",2]}", "{\"z\":[null,\"user\"]}", "{\"m\":[\"1\",0]}",
            "{\"m\":[1,-0.1]}", "{\"g\":[]}",
            "{\"g\":\"POLITE\",\"x\":{\"gesture\":\"NONE\"}}",
            "{\"f\":[\"warm\",0.5],\"x\":{\"facialExpression\":null}}",
            "{\"z\":[\"forward\",\"user\"],\"x\":{\"gaze\":{}}}",
            "{\"m\":[1,0],\"x\":{\"motion\":{}}}"
    })
    void malformedCompactOutputFailsOnceWithoutPublishingSpeech(String nv) {
        var gateway = new Gateway(COMPACT);
        var agent = new Agent("synthetic", "compact fixture", new State("s", policy(), List.of()));
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
        agent.start(runtime);
        int historySize = agent.getEventHistory().toList().size();
        gateway.raw = "{\"speech\":\"private user text\",\"nv\":" + nv + "}";
        gateway.calls = 0;
        var failure = assertThrows(IllegalStateException.class, () -> agent.generate(runtime));
        assertFalse(failure.getMessage().contains("private user text"));
        assertEquals(1, gateway.calls);
        assertEquals(historySize, agent.getEventHistory().toList().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"speech\":\"Hello\",\"nv\":{},\"nonVerbal\":{}}",
            "{\"speech\":\"Hello\",\"nv\":{},\"unexpected\":true}",
            "{\"speech\":\"Hello\",\"nv\":{},\"motion\":[]}",
            "{\"speech\":\"Hello\",\"nv\":{},\"display\":false}",
            "{\"nv\":{}}"
    })
    void ambiguousOrInvalidEnvelopesAreRejected(String raw) {
        assertThrows(IllegalStateException.class, () -> BehaviourPlanInference.parse(raw));
    }

    @Test void emptyCompactAndCanonicalEnvelopesUseExistingGestureDefault() {
        assertEquals(BehaviourPlanInference.parse("{\"speech\":\"Hi\",\"nonVerbal\":{}}").toJsonObject(),
                BehaviourPlanInference.parse("{\"speech\":\"Hi\",\"nv\":{}}").toJsonObject());
        assertEquals("NONE", BehaviourPlanInference.parse("{\"speech\":\"Hi\",\"nv\":{\"g\":null}}")
                .getNonVerbal().getAsJsonObject().get("gesture").getAsString());
    }

    @Test void expansionDoesNotMutateProviderObjectOrShareCustomObjects() {
        var provider = JsonParser.parseString("""
                {"speech":"Hi","nv":{"f":["warm",0.3],"x":{"posture":{"type":"open"}}},
                 "display":{"mode":"banner"}}
                """).getAsJsonObject();
        var original = provider.deepCopy();
        var expanded = CompactBehaviourPlan.expand(provider);
        expanded.getAsJsonObject("nonVerbal").getAsJsonObject("posture").addProperty("type", "changed");
        expanded.getAsJsonObject("display").addProperty("mode", "changed");
        assertEquals(original, provider);
    }

    private static PromptPolicy policy() {
        var policy = new PromptPolicy("task instructions", "starter", "summary");
        policy.setNonVerbalPlanPrompt("custom nonverbal instructions");
        return policy;
    }

    private static final class Gateway extends NoOpLanguageModelGateway {
        String raw;
        int calls;
        InferenceRequest request;
        Gateway(String raw) { this.raw = raw; }
        @Override public String infer(InferenceRequest request) {
            this.request = request;
            calls++;
            return raw;
        }
    }
}
