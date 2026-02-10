package ch.zhaw.prometheus.model.policy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.utils.NamedParametersFormatter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

@Entity
public class PromptPolicy extends Policy {
    public static final String DEFAULT_SUMMARISE_PROMPT = "Please summarise the following event history. Be concise, but ensure that the key points and issues are included. ";

    @Column(length = 3000)
    private String promptTemplate;
    @Column(length = 3000)
    private String starterPrompt;
    @Column(length = 3000)
    private String summarisePrompt;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> storageKeysFrom;

    @Enumerated(EnumType.STRING)
    private PromptValueShape expectedShape;
    @Transient
    private PromptPolicy outerPolicy;

    public PromptPolicy() {
        this("", null, DEFAULT_SUMMARISE_PROMPT, null, List.of(), PromptValueShape.NONE, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt) {
        this(promptTemplate, starterPrompt, summarisePrompt, null, List.of(), PromptValueShape.NONE, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt, Storage storage,
            List<String> storageKeysFrom) {
        this(promptTemplate, starterPrompt, summarisePrompt, storage, storageKeysFrom, PromptValueShape.NONE, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt, Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape) {
        this(promptTemplate, starterPrompt, summarisePrompt, storage, storageKeysFrom, expectedShape, null);
    }

    private PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt,
            Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape, PromptPolicy outerPolicy) {
        this.promptTemplate = promptTemplate == null ? "" : promptTemplate;
        this.starterPrompt = starterPrompt;
        this.summarisePrompt = summarisePrompt;
        this.storage = storage;
        this.storageKeysFrom = storageKeysFrom == null ? List.of() : List.copyOf(storageKeysFrom);
        this.expectedShape = expectedShape == null ? PromptValueShape.NONE : expectedShape;
        this.outerPolicy = outerPolicy;
    }

    @Override
    public Policy withOuterPolicy(Policy outerPolicy) {
        if (outerPolicy == null) {
            return this;
        }
        if (!(outerPolicy instanceof PromptPolicy promptOuter)) {
            throw new IllegalArgumentException("cannot compose prompt policy with " + outerPolicy.getClass().getName());
        }
        return new PromptPolicy(this.promptTemplate, this.starterPrompt, this.summarisePrompt, this.storage,
                this.storageKeysFrom, this.expectedShape, promptOuter);
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return null;
        }
        List<PromptMessage> messages = assembler.compose(events, prompt, this.starterPrompt);
        String speech = languageModelGateway.complete(messages);
        if (speech == null || speech.isBlank()) {
            return null;
        }
        return BehaviourPlan.speechOnly(speech);
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return null;
        }
        List<PromptMessage> messages = assembler.compose(events, prompt);
        String speech = languageModelGateway.complete(messages);
        if (speech == null || speech.isBlank()) {
            return null;
        }
        return BehaviourPlan.speechOnly(speech);
    }

    @Override
    public String summarise(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        if (this.summarisePrompt == null || this.summarisePrompt.isBlank()) {
            return null;
        }
        List<PromptMessage> messages = assembler.composeCondensed(events, this.summarisePrompt);
        return languageModelGateway.summariseOffline(messages);
    }

    @Override
    public String describe() {
        return resolvePrompt();
    }

    @Override
    public boolean decide(EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway languageModelGateway) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return false;
        }
        List<PromptMessage> messages = assembler.composeCondensed(events, prompt,
                LanguageModelGateway.REMINDER_DECISION);
        return languageModelGateway.decide(messages);
    }

    @Override
    public JsonElement extract(EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return null;
        }
        List<PromptMessage> messages = assembler.composeCondensed(events, prompt,
                LanguageModelGateway.REMINDER_EXTRACTION);
        return languageModelGateway.extract(messages);
    }

    @Override
    public JsonElement summarise(EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return null;
        }
        List<PromptMessage> messages = assembler.composeCondensed(events, prompt,
                LanguageModelGateway.REMINDER_SUMMARISATION);
        return languageModelGateway.summarise(messages);
    }

    private String resolvePrompt() {
        String resolved = resolveOwnPrompt();
        if (this.outerPolicy == null) {
            return resolved;
        }
        String outerResolved = this.outerPolicy.resolvePrompt();
        if (outerResolved.isBlank()) {
            return resolved;
        }
        if (resolved.isBlank()) {
            return outerResolved;
        }
        return (outerResolved + " " + resolved).trim();
    }

    private String resolveOwnPrompt() {
        if (this.storage == null || this.storageKeysFrom == null || this.storageKeysFrom.isEmpty()) {
            return this.promptTemplate.trim();
        }
        Map<String, JsonElement> valuesForKeys = new HashMap<>();
        for (String currentKey : this.storageKeysFrom) {
            valuesForKeys.put(currentKey, this.storage.get(currentKey));
        }
        validateValues(valuesForKeys);
        return NamedParametersFormatter.format(this.promptTemplate, valuesForKeys).trim();
    }

    private void validateValues(Map<String, JsonElement> valuesForKeys) {
        if (this.expectedShape == null || this.expectedShape == PromptValueShape.NONE) {
            return;
        }
        JsonElement first = valuesForKeys.values().iterator().next();
        switch (this.expectedShape) {
            case ARRAY -> {
                if (!(first instanceof com.google.gson.JsonArray)) {
                    throw new RuntimeException(
                            "expected storageKeyFrom being associated to a list (JsonArray) but enountered "
                                    + first.getClass()
                                    + " instead");
                }
            }
            case OBJECT -> {
                if (!(first instanceof com.google.gson.JsonObject)) {
                    throw new RuntimeException(
                            "expected storageKeyFrom being associated to an object (JsonObject) but enountered "
                                    + first.getClass()
                                    + " instead");
                }
            }
            case PRIMITIVE -> {
                if (!(first instanceof com.google.gson.JsonPrimitive)) {
                    throw new RuntimeException(
                            "expected storageKeyFrom being associated to a primitive (JsonPrimitive) but enountered "
                                    + first.getClass()
                                    + " instead");
                }
            }
            default -> {
            }
        }
    }
}

