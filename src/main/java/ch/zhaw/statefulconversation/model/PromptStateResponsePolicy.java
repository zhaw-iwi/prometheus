package ch.zhaw.statefulconversation.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

import ch.zhaw.statefulconversation.spi.LMOpenAI;
import ch.zhaw.statefulconversation.utils.NamedParametersFormatter;
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
public class PromptStateResponsePolicy extends StateResponsePolicy {
    public static final String DEFAULT_SUMMARISE_PROMPT = "Please summarise the following conversation. Be concise, but ensure that the key points and issues are included. ";

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
    private PromptStateResponsePolicy outerPolicy;

    public PromptStateResponsePolicy() {
        this("", null, DEFAULT_SUMMARISE_PROMPT, null, List.of(), PromptValueShape.NONE, null);
    }

    public PromptStateResponsePolicy(String promptTemplate, String starterPrompt, String summarisePrompt) {
        this(promptTemplate, starterPrompt, summarisePrompt, null, List.of(), PromptValueShape.NONE, null);
    }

    public PromptStateResponsePolicy(String promptTemplate, String starterPrompt, String summarisePrompt, Storage storage,
            List<String> storageKeysFrom) {
        this(promptTemplate, starterPrompt, summarisePrompt, storage, storageKeysFrom, PromptValueShape.NONE, null);
    }

    public PromptStateResponsePolicy(String promptTemplate, String starterPrompt, String summarisePrompt, Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape) {
        this(promptTemplate, starterPrompt, summarisePrompt, storage, storageKeysFrom, expectedShape, null);
    }

    private PromptStateResponsePolicy(String promptTemplate, String starterPrompt, String summarisePrompt,
            Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape, PromptStateResponsePolicy outerPolicy) {
        this.promptTemplate = promptTemplate == null ? "" : promptTemplate;
        this.starterPrompt = starterPrompt;
        this.summarisePrompt = summarisePrompt;
        this.storage = storage;
        this.storageKeysFrom = storageKeysFrom == null ? List.of() : List.copyOf(storageKeysFrom);
        this.expectedShape = expectedShape == null ? PromptValueShape.NONE : expectedShape;
        this.outerPolicy = outerPolicy;
    }

    @Override
    public StateResponsePolicy withOuterPolicy(StateResponsePolicy outerPolicy) {
        if (outerPolicy == null) {
            return this;
        }
        if (!(outerPolicy instanceof PromptStateResponsePolicy promptOuter)) {
            throw new IllegalArgumentException("cannot compose prompt policy with " + outerPolicy.getClass().getName());
        }
        return new PromptStateResponsePolicy(this.promptTemplate, this.starterPrompt, this.summarisePrompt, this.storage,
                this.storageKeysFrom, this.expectedShape, promptOuter);
    }

    @Override
    public String onStart(State state) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return null;
        }
        return LMOpenAI.complete(state.getEventHistory(), prompt, this.starterPrompt, state.getName());
    }

    @Override
    public String onRespond(State state) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return null;
        }
        return LMOpenAI.complete(state.getEventHistory(), prompt, state.getName());
    }

    @Override
    public String summarise(State state) {
        if (this.summarisePrompt == null || this.summarisePrompt.isBlank()) {
            return null;
        }
        return LMOpenAI.summariseOffline(state.getEventHistory(), this.summarisePrompt);
    }

    @Override
    public String describe() {
        return resolvePrompt();
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
            default -> {
            }
        }
    }
}
