package ch.zhaw.prometheus.model.policy;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
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
    public static final String DEFAULT_NONVERBAL_GESTURE_PROMPT = """
            Select one nonverbal gesture label that best supports the assistant speech.
            Allowed labels only:
            OPEN_QUESTION
            EXPLAIN
            UNCERTAIN
            ACKNOWLEDGE
            POLITE
            NONE
            Return only the label.
            """;

    @Column(length = 3000)
    private String promptTemplate;
    @Column(length = 3000)
    private String starterPrompt;
    @Column(length = 3000)
    private String summarisePrompt;
    @Column(length = 3000)
    private String nonVerbalGesturePrompt;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> storageKeysFrom;

    @Enumerated(EnumType.STRING)
    private PromptValueShape expectedShape;
    @Transient
    private PromptPolicy outerPolicy;

    public PromptPolicy() {
        this("", null, DEFAULT_SUMMARISE_PROMPT, null, null, List.of(), PromptValueShape.NONE, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt) {
        this(promptTemplate, starterPrompt, summarisePrompt, null, null, List.of(), PromptValueShape.NONE, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt, Storage storage,
            List<String> storageKeysFrom) {
        this(promptTemplate, starterPrompt, summarisePrompt, null, storage, storageKeysFrom, PromptValueShape.NONE, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt, Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape) {
        this(promptTemplate, starterPrompt, summarisePrompt, null, storage, storageKeysFrom, expectedShape, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt,
            String nonVerbalGesturePrompt, Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape) {
        this(promptTemplate, starterPrompt, summarisePrompt, nonVerbalGesturePrompt, storage, storageKeysFrom,
                expectedShape, null);
    }

    private PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt,
            String nonVerbalGesturePrompt,
            Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape, PromptPolicy outerPolicy) {
        this.promptTemplate = promptTemplate == null ? "" : promptTemplate;
        this.starterPrompt = starterPrompt;
        this.summarisePrompt = summarisePrompt;
        this.nonVerbalGesturePrompt = nonVerbalGesturePrompt;
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
        return new PromptPolicy(this.promptTemplate, this.starterPrompt, this.summarisePrompt,
                this.nonVerbalGesturePrompt, this.storage,
                this.storageKeysFrom, this.expectedShape, promptOuter);
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.onStart(state, events, assembler, languageModelGateway, OutputProfile.FULL_PLAN);
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway, OutputProfile outputProfile) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return null;
        }
        List<PromptMessage> messages = assembler.compose(events, prompt, this.starterPrompt);
        return this.producePlan(messages, languageModelGateway, events, outputProfile);
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.onRespond(state, events, assembler, languageModelGateway, OutputProfile.FULL_PLAN);
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway, OutputProfile outputProfile) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return null;
        }
        List<PromptMessage> messages = assembler.compose(events, prompt);
        return this.producePlan(messages, languageModelGateway, events, outputProfile);
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
        return this.describe(OutputProfile.FULL_PLAN);
    }

    @Override
    public String describe(OutputProfile outputProfile) {
        String basePrompt = resolvePrompt();
        OutputProfile resolved = outputProfile == null ? OutputProfile.FULL_PLAN : outputProfile;
        String contract = switch (resolved) {
            case REALTIME_SPEECH ->
                "Output contract: respond with natural spoken assistant text only. Do not output JSON, code blocks, or schema wrappers.";
            case BACKEND_COMPLEMENT ->
                "Output contract: produce non-speech behaviour only as compact JSON with keys from {nonVerbal,motion,display}. Never include speech.";
            case FULL_PLAN -> "";
        };
        if (contract.isBlank()) {
            return basePrompt;
        }
        if (basePrompt == null || basePrompt.isBlank()) {
            return contract;
        }
        return (basePrompt + " " + contract).trim();
    }

    private BehaviourPlan producePlan(List<PromptMessage> messages, LanguageModelGateway languageModelGateway,
            EventHistory events, OutputProfile outputProfile) {
        OutputProfile resolved = outputProfile == null ? OutputProfile.FULL_PLAN : outputProfile;
        return switch (resolved) {
            case REALTIME_SPEECH -> buildRealtimeSpeechPlan(messages, languageModelGateway);
            case BACKEND_COMPLEMENT -> buildBackendComplementPlan(events, languageModelGateway);
            case FULL_PLAN -> buildFullPlan(messages, languageModelGateway);
        };
    }

    private BehaviourPlan buildRealtimeSpeechPlan(List<PromptMessage> messages, LanguageModelGateway languageModelGateway) {
        String speech = languageModelGateway.complete(messages);
        if (speech == null || speech.isBlank()) {
            return null;
        }
        return BehaviourPlan.speechOnly(speech);
    }

    private BehaviourPlan buildFullPlan(List<PromptMessage> messages, LanguageModelGateway languageModelGateway) {
        String speech = languageModelGateway.complete(messages);
        if (speech == null || speech.isBlank()) {
            return null;
        }
        BehaviourPlan plan = BehaviourPlan.speechOnly(speech);
        plan.setNonVerbal(resolveNonVerbalGesture(speech, languageModelGateway));
        return plan;
    }

    private BehaviourPlan buildBackendComplementPlan(EventHistory events, LanguageModelGateway languageModelGateway) {
        String assistantSpeech = latestAssistantSpeech(events);
        if (assistantSpeech == null || assistantSpeech.isBlank()) {
            return null;
        }
        BehaviourPlan plan = new BehaviourPlan();
        plan.setNonVerbal(resolveNonVerbalGesture(assistantSpeech, languageModelGateway));
        return plan.isEmpty() ? null : plan;
    }

    private static String latestAssistantSpeech(EventHistory events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        List<Event> history = events.toList();
        for (int i = history.size() - 1; i >= 0; i--) {
            Event event = history.get(i);
            if (event == null || !Event.ACTOR_ASSISTANT.equals(event.getActor())) {
                continue;
            }
            if (Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
                BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
                if (plan != null && plan.getSpeech() != null && !plan.getSpeech().isBlank()) {
                    return plan.getSpeech();
                }
            }
            if (event.getPayload() != null && !event.getPayload().isBlank()) {
                return event.getPayload();
            }
        }
        return null;
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

    public String getNonVerbalGesturePrompt() {
        return this.nonVerbalGesturePrompt;
    }

    public void setNonVerbalGesturePrompt(String nonVerbalGesturePrompt) {
        this.nonVerbalGesturePrompt = nonVerbalGesturePrompt;
    }

    private JsonElement resolveNonVerbalGesture(String speech, LanguageModelGateway languageModelGateway) {
        if (this.nonVerbalGesturePrompt == null || this.nonVerbalGesturePrompt.isBlank()) {
            return null;
        }
        if (speech == null || speech.isBlank()) {
            return null;
        }
        List<PromptMessage> messages = List.of(
                PromptMessage.system(this.nonVerbalGesturePrompt),
                PromptMessage.user("Assistant speech: " + speech));
        String raw = languageModelGateway.complete(messages);
        String gesture = normalizeGestureLabel(raw);
        if (gesture == null) {
            gesture = "NONE";
        }
        JsonObject nonVerbal = new JsonObject();
        nonVerbal.addProperty("gesture", gesture);
        return nonVerbal;
    }

    private static String normalizeGestureLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String fromJson = parseGestureFromJson(raw);
        if (fromJson != null) {
            return fromJson;
        }
        String normalized = raw.trim()
                .replace("\"", "")
                .replace("'", "")
                .toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        return switch (normalized) {
            case "OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE", "NONE" -> normalized;
            default -> null;
        };
    }

    private static String parseGestureFromJson(String raw) {
        try {
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            if (!json.has("gesture") || json.get("gesture").isJsonNull()) {
                return null;
            }
            return normalizeGestureLabel(json.get("gesture").getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }
}

