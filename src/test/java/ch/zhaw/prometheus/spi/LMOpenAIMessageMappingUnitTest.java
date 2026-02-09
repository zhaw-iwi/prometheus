package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.event.Event;

class LMOpenAIMessageMappingUnitTest {

    @Test
    void mapsRolesForSystemUserAndAssistantEvents() {
        Event system = Event.systemPrompt("system prompt", "s");
        Event user = Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello", null, "s");
        Event assistant = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "hi",
                "{\"speech\":\"hi\"}", "s");

        assertEquals("system", LMOpenAI.mapRole(system));
        assertEquals("user", LMOpenAI.mapRole(user));
        assertEquals("assistant", LMOpenAI.mapRole(assistant));
    }

    @Test
    void mapsBehaviourPlanContentFromPayloadSpeechFirst() {
        Event assistantPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "fallback-content", "{\"speech\":\"payload-speech\"}", "s");

        assertEquals("payload-speech", LMOpenAI.mapContent(assistantPlan));
    }

    @Test
    void fallsBackToContentWhenPayloadCannotBeParsed() {
        Event assistantPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "fallback-content", "{invalid-json", "s");

        assertEquals("fallback-content", LMOpenAI.mapContent(assistantPlan));
    }

    @Test
    void buildsOpenAIMessagesWithRoleAndContentFields() {
        Event system = Event.systemPrompt("be helpful", "s");
        Event user = Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello", null, "s");
        Event assistantPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                null, "{\"speech\":\"payload-speech\"}", "s");

        JsonArray messages = LMOpenAI.toOpenAIMessages(List.of(system, user, assistantPlan));

        assertEquals(3, messages.size());

        JsonObject first = messages.get(0).getAsJsonObject();
        assertEquals("system", first.get("role").getAsString());
        assertEquals("be helpful", first.get("content").getAsString());

        JsonObject third = messages.get(2).getAsJsonObject();
        assertEquals("assistant", third.get("role").getAsString());
        assertEquals("payload-speech", third.get("content").getAsString());
    }
}
