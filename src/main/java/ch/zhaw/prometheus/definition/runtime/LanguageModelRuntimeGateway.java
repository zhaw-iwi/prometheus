package ch.zhaw.prometheus.definition.runtime;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

/** Adapts the existing provider SPI to the trusted declarative runtime boundary. */
@Component
public final class LanguageModelRuntimeGateway implements RuntimeModelGateway {
    private final PromptMessageAssembler assembler;
    private final LanguageModelGateway gateway;
    private final ObjectMapper objectMapper;

    public LanguageModelRuntimeGateway(PromptMessageAssembler assembler, LanguageModelGateway gateway,
            ObjectMapper objectMapper) {
        this.assembler = assembler;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
        List<PromptMessage> messages = promptMessages(prompts, invocation);
        String speech = this.gateway.complete(messages);
        if (speech == null || speech.isBlank()) {
            return null;
        }
        JsonNode nonVerbal = nonVerbal(prompts, speech);
        return new RuntimeBehaviour(speech, nonVerbal == null ? null : new ImmutableJson(nonVerbal), null, null);
    }

    public List<PromptMessage> promptMessages(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
        EventHistory history = history(invocation);
        String append = prompts.starting() && prompts.starterPrompt() != null && !prompts.starterPrompt().isBlank()
                ? prompts.starterPrompt() : null;
        return append == null
                ? this.assembler.compose(history, prompts.responsePrompt())
                : this.assembler.compose(history, prompts.responsePrompt(), append);
    }

    @Override
    public boolean decide(String prompt, RuntimeInvocation invocation) {
        return this.gateway.decide(this.assembler.composeCondensed(history(invocation), prompt,
                LanguageModelGateway.REMINDER_DECISION));
    }

    @Override
    public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
        var extracted = this.gateway.extract(this.assembler.composeCondensed(history(invocation), prompt,
                LanguageModelGateway.REMINDER_EXTRACTION));
        if (extracted == null) {
            return null;
        }
        try {
            return this.objectMapper.readTree(extracted.toString());
        } catch (Exception invalidProviderJson) {
            throw new IllegalStateException("Language model extraction did not return valid JSON", invalidProviderJson);
        }
    }

    private JsonNode nonVerbal(RuntimePromptBundle prompts, String speech) {
        JsonNode plan = completeJson(prompts.nonverbalPlanPrompt(), speech);
        if (plan != null && plan.isObject()) {
            JsonNode nested = plan.get("nonVerbal");
            ObjectNode normalized = (nested != null && nested.isObject() ? (ObjectNode) nested : (ObjectNode) plan)
                    .deepCopy();
            JsonNode gesture = normalized.get("gesture");
            if (gesture != null && gesture.isTextual()) {
                String label = gesture(gesture.asText());
                if (label == null) {
                    normalized.remove("gesture");
                } else {
                    normalized.put("gesture", label);
                }
            }
            return normalized.isEmpty() ? null : normalized;
        }
        if (prompts.gesturePrompt() == null || prompts.gesturePrompt().isBlank()) {
            return null;
        }
        String raw = this.gateway.complete(List.of(PromptMessage.system(prompts.gesturePrompt()),
                PromptMessage.user("Assistant speech: " + speech)));
        String label = gesture(raw);
        return label == null ? null : JsonNodeFactory.instance.textNode(label);
    }

    private JsonNode completeJson(String prompt, String speech) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String raw = this.gateway.complete(List.of(PromptMessage.system(prompt),
                PromptMessage.user("Assistant speech: " + speech)));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return this.objectMapper.readTree(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String gesture(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replace("\"", "").replace("'", "")
                .toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE", "NONE" -> normalized;
            default -> null;
        };
    }

    private static EventHistory history(RuntimeInvocation invocation) {
        return new EventHistory(invocation.history().stream().map(Event::fromRuntime).toList());
    }
}
