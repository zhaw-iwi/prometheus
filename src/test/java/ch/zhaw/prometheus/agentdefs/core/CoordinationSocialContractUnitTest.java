package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.social.SocialSituationChangeDetector;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationSocialContractUnitTest {
    static JsonObject socialProposal(String field, String value) {
        var proposal = CoordinationAgentUnitTest.sensor(false);
        proposal.addProperty("goal", field.equals("changeType") ? "Offer to return when another person enters" : "Offer once when at least two people are visible");
        var task = proposal.getAsJsonObject("reactions"); task.addProperty("goal", proposal.get("goal").getAsString());
        task.addProperty("maxActions", 1); var rule = task.getAsJsonArray("rules").get(0).getAsJsonObject();
        task.getAsJsonArray("rules").remove(1);
        rule.addProperty("eventType", field.equals("changeType") ? Event.TYPE_SOCIAL_SITUATION_CHANGE : Event.TYPE_HUMAN_PRESENCE);
        rule.addProperty("field", field); rule.addProperty("operator", field.equals("changeType") ? "eq" : "gt");
        rule.add("value", JsonParser.parseString(value)); rule.addProperty("minConfidence", 0);
        rule.addProperty("action", "Offer to continue another time"); return proposal;
    }

    @Test void extraPersonCannotBeAcceptedAsFirstArrivalOrSilentlyStartedAsAnAlternative() {
        var f = new CoordinationAgentUnitTest();
        var result = f.turn("PROPOSE", socialProposal("changeType", "\"arrival\""), "Propose a response when another person enters", f.text);
        assertTrue(result.getPayload().contains("empty scene becoming occupied"));
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        f.turn("PROPOSE_AND_START", socialProposal("humanCount", "1"), "Start now when another person enters", f.text);
        assertEquals("CONVERSATION", CoordinationMemory.stage(f.storage()));
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
        f.storage().put(CoordinationMemory.DRAFT, socialProposal("changeType", "\"arrival\""));
        var help = CoordinationSupport.explain(f.storage(), Instant.now());
        assertTrue(help.contains("cannot start unchanged")); assertFalse(help.contains("Say Start"));
        assertFalse(CoordinationMemory.acceptancePrompt(f.storage()).contains("Shall we start"));
    }

    @Test void explicitReviewedCountAlternativeArmsThenActsOnceOnMatchingFreshEvidence() {
        var f = new CoordinationAgentUnitTest();
        f.propose(socialProposal("humanCount", "1"), f.text); f.start(f.text);
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt()); f.openCueWindow();
        org.mockito.Mockito.doReturn("{\"speech\":\"We can continue another time.\"}").when(f.gateway).infer(org.mockito.ArgumentMatchers.any());
        f.agent.acknowledge(Event.observation(Event.TYPE_HUMAN_PRESENCE, "camera", "{\"humanCount\":1,\"ts\":\"" + Instant.now() + "\"}"), f.text);
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        var response = f.agent.acknowledge(Event.observation(Event.TYPE_HUMAN_PRESENCE, "camera", "{\"humanCount\":2,\"ts\":\"" + Instant.now() + "\"}"), f.text);
        assertNotNull(response); assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
    }

    @ParameterizedTest
    @CsvSource({"0,1,arrival", "1,2,group_size_changed", "2,3,crowd_detected", "0,3,crowd_detected", "1,0,departure", "2,1,single_person_nearby", "1,1,none"})
    void detectorCategoriesRetainTheirActualMeaningAndPrecedence(int before, int after, String expected) {
        var history = new EventHistory(); history.appendEvent(group(before, before, before == 0 ? 0 : 1));
        history.appendEvent(group(after, after, after == 0 ? 0 : 1));
        var result = SocialSituationChangeDetector.defaultThresholds().detect(history);
        assertEquals(expected, result.map(event -> JsonParser.parseString(event.getPayload()).getAsJsonObject().get("changeType").getAsString()).orElse("none"));
    }
    @Test void groupingOnlyChangeIsNotEvidenceOfAnExtraPerson() {
        var history = new EventHistory(); history.appendEvent(group(2, 2, 1)); history.appendEvent(group(2, 0, 2));
        var result = SocialSituationChangeDetector.defaultThresholds().detect(history).orElseThrow();
        var data = JsonParser.parseString(result.getPayload()).getAsJsonObject();
        assertEquals("group_size_changed", data.get("changeType").getAsString());
        assertEquals(data.get("previousHumanCount"), data.get("currentHumanCount"));
    }
    static Event group(int humans, int singleton, int largest) {
        return Event.observation(Event.TYPE_SOCIAL_GROUPING, "camera", "{\"humanCount\":" + humans
                + ",\"groupCount\":" + (singleton == 0 && humans > 1 ? 1 : 0) + ",\"singletonCount\":" + singleton
                + ",\"largestGroupSize\":" + largest + "}");
    }
}
