package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.policy.PromptMessage;

class OpenAILanguageModelGatewayMessageMappingUnitTest {

    private final OpenAILanguageModelGateway gateway = new OpenAILanguageModelGateway(new OpenAIProperties());

    @Test
    void buildsOpenAIMessagesWithRoleAndContentFields() {
        PromptMessage system = PromptMessage.system("be helpful");
        PromptMessage user = PromptMessage.user("hello");
        PromptMessage assistantPlan = PromptMessage.assistant("payload-speech");

        JsonArray messages = gateway.toOpenAIMessages(List.of(system, user, assistantPlan));

        assertEquals(3, messages.size());

        JsonObject first = messages.get(0).getAsJsonObject();
        assertEquals("system", first.get("role").getAsString());
        assertEquals("be helpful", first.get("content").getAsString());

        JsonObject third = messages.get(2).getAsJsonObject();
        assertEquals("assistant", third.get("role").getAsString());
        assertEquals("payload-speech", third.get("content").getAsString());
    }

    @Test
    void returnsEmptyArrayForNullOrEmptyMessages() {
        assertEquals(0, gateway.toOpenAIMessages(null).size());
        assertEquals(0, gateway.toOpenAIMessages(List.of()).size());
    }
}


