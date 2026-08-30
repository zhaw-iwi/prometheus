package ch.zhaw.prometheus.definition.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class LanguageModelRuntimeGatewayUnitTest {

    @Test
    void composesRuntimeHistoryAndStarterPromptForProviderGeneration() {
        RecordingGateway provider = new RecordingGateway();
        provider.completion = "Hello there";
        LanguageModelRuntimeGateway gateway = runtimeGateway(provider);
        RuntimeInvocation invocation = invocation();

        RuntimeBehaviour result = gateway.generate(
                new RuntimePromptBundle("Respond helpfully", "Begin now", "", "", "", true), invocation);

        assertEquals("Hello there", result.speech());
        assertEquals(List.of("system", "user", "system"),
                provider.messages.stream().map(PromptMessage::getRole).toList());
        assertEquals("Respond helpfully", provider.messages.getFirst().getContent());
        assertEquals("Begin now", provider.messages.getLast().getContent());
    }

    @Test
    void convertsGsonExtractionIntoRuntimeJacksonJson() {
        RecordingGateway provider = new RecordingGateway();
        provider.extraction = JsonParser.parseString("{\"topic\":\"care\",\"score\":2}");
        LanguageModelRuntimeGateway gateway = runtimeGateway(provider);

        var result = gateway.extract("Extract the topic", new ObjectMapper().createObjectNode(), invocation());

        assertEquals("care", result.path("topic").asText());
        assertEquals(2, result.path("score").asInt());
        assertTrue(provider.messages.getLast().getContent().contains("Return valid JSON data"));
    }

    private static LanguageModelRuntimeGateway runtimeGateway(RecordingGateway provider) {
        return new LanguageModelRuntimeGateway(new PromptMessageAssembler(), provider, new ObjectMapper());
    }

    private static RuntimeInvocation invocation() {
        RuntimeEvent event = new RuntimeEvent(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER,
                Event.KIND_OBSERVATION, "I need help");
        return new RuntimeInvocation("conversation", List.of("conversation"), List.of(event), Map.of());
    }

    private static final class RecordingGateway implements LanguageModelGateway {
        private List<PromptMessage> messages = new ArrayList<>();
        private String completion;
        private JsonElement extraction;

        @Override
        public String complete(List<PromptMessage> messages) {
            this.messages = List.copyOf(messages);
            return this.completion;
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            this.messages = List.copyOf(messages);
            return true;
        }

        @Override
        public JsonElement extract(List<PromptMessage> messages) {
            this.messages = List.copyOf(messages);
            return this.extraction;
        }

        @Override
        public JsonElement summarise(List<PromptMessage> messages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            throw new UnsupportedOperationException();
        }
    }
}
