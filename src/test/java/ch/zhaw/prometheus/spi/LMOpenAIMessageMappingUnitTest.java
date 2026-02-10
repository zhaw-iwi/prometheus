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
        Event system = Event.systemPrompt("system prompt");
        Event user = Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello");
        Event assistant = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"hi\"}");

        assertEquals("system", LMOpenAI.mapRole(system));
        assertEquals("user", LMOpenAI.mapRole(user));
        assertEquals("assistant", LMOpenAI.mapRole(assistant));
    }

    @Test
    void mapsBehaviourPlanContentFromPayloadSpeechFirst() {
        Event assistantPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"payload-speech\"}");

        assertEquals("payload-speech", LMOpenAI.mapContent(assistantPlan));
    }

    @Test
    void fallsBackToContentWhenPayloadCannotBeParsed() {
        Event assistantPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{invalid-json");

        assertEquals("{invalid-json", LMOpenAI.mapContent(assistantPlan));
    }

    @Test
    void buildsOpenAIMessagesWithRoleAndContentFields() {
        Event system = Event.systemPrompt("be helpful");
        Event user = Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello");
        Event assistantPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"payload-speech\"}");

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
