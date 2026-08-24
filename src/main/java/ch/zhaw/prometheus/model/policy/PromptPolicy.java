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
    public static final String DEFAULT_NONVERBAL_PLAN_PROMPT = """
            Produce a compact JSON object for assistant nonverbal behaviour that supports the assistant speech.
            Output STRICT JSON only. No markdown, no code fences.

            Required top-level key:
            - "gesture": one of OPEN_QUESTION, EXPLAIN, UNCERTAIN, ACKNOWLEDGE, POLITE, NONE

            Optional keys:
            - "facialExpression": {"type":"string","intensity":0.0-1.0}
            - "gaze": {"direction":"string","focus":"string"}
            - "posture": {"type":"string","lean":"string","openness":0.0-1.0}
            - "prosody": {"rate":"string","pitch":"string","volume":"string"}
            - "proxemics": {"distance":"string"}
            - "motion": {"stillness":0.0-1.0,"energy":0.0-1.0}

            Keep values concise and plausible for the provided speech.
            """;

    @Column(columnDefinition = "TEXT")
    private String promptTemplate;
    @Column(columnDefinition = "TEXT")
    private String starterPrompt;
    @Column(columnDefinition = "TEXT")
    private String summarisePrompt;
    @Column(columnDefinition = "TEXT")
    private String nonVerbalGesturePrompt;
    @Column(columnDefinition = "TEXT")
    private String nonVerbalPlanPrompt;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> storageKeysFrom;

    @Enumerated(EnumType.STRING)
    private PromptValueShape expectedShape;
    @Transient
    private PromptPolicy outerPolicy;

    public PromptPolicy() {
        this("", null, DEFAULT_SUMMARISE_PROMPT, null, null, null, List.of(), PromptValueShape.NONE, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt) {
        this(promptTemplate, starterPrompt, summarisePrompt, null, null, null, List.of(), PromptValueShape.NONE,
                null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt, Storage storage,
            List<String> storageKeysFrom) {
        this(promptTemplate, starterPrompt, summarisePrompt, null, null, storage, storageKeysFrom,
                PromptValueShape.NONE, null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt, Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape) {
        this(promptTemplate, starterPrompt, summarisePrompt, null, null, storage, storageKeysFrom, expectedShape,
                null);
    }

    public PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt,
            String nonVerbalGesturePrompt, Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape) {
        this(promptTemplate, starterPrompt, summarisePrompt, nonVerbalGesturePrompt, null, storage, storageKeysFrom,
                expectedShape, null);
    }

    private PromptPolicy(String promptTemplate, String starterPrompt, String summarisePrompt,
            String nonVerbalGesturePrompt,
            String nonVerbalPlanPrompt,
            Storage storage,
            List<String> storageKeysFrom, PromptValueShape expectedShape, PromptPolicy outerPolicy) {
        this.promptTemplate = promptTemplate == null ? "" : promptTemplate;
        this.starterPrompt = starterPrompt;
        this.summarisePrompt = summarisePrompt;
        this.nonVerbalGesturePrompt = nonVerbalGesturePrompt;
        this.nonVerbalPlanPrompt = nonVerbalPlanPrompt;
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
                this.nonVerbalGesturePrompt, this.nonVerbalPlanPrompt, this.storage,
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
        return this.producePlan(messages, languageModelGateway);
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
        return this.producePlan(messages, languageModelGateway);
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

    private BehaviourPlan producePlan(List<PromptMessage> messages, LanguageModelGateway languageModelGateway) {
        return buildFullPlan(messages, languageModelGateway);
    }

    private BehaviourPlan buildFullPlan(List<PromptMessage> messages, LanguageModelGateway languageModelGateway) {
        String speech = languageModelGateway.complete(messages);
        if (speech == null || speech.isBlank()) {
            return null;
        }
        BehaviourPlan plan = BehaviourPlan.speechOnly(speech);
        plan.setNonVerbal(resolveNonVerbal(speech, languageModelGateway));
        return plan;
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

    public String getNonVerbalPlanPrompt() {
        return this.nonVerbalPlanPrompt;
    }

    public void setNonVerbalPlanPrompt(String nonVerbalPlanPrompt) {
        this.nonVerbalPlanPrompt = nonVerbalPlanPrompt;
    }

    private JsonElement resolveNonVerbal(String speech, LanguageModelGateway languageModelGateway) {
        JsonElement plan = resolveNonVerbalPlan(speech, languageModelGateway);
        if (plan != null) {
            return plan;
        }
        return resolveNonVerbalGesture(speech, languageModelGateway);
    }

    private JsonElement resolveNonVerbalPlan(String speech, LanguageModelGateway languageModelGateway) {
        if (this.nonVerbalPlanPrompt == null || this.nonVerbalPlanPrompt.isBlank()) {
            return null;
        }
        if (speech == null || speech.isBlank()) {
            return null;
        }
        List<PromptMessage> messages = List.of(
                PromptMessage.system(this.nonVerbalPlanPrompt),
                PromptMessage.user("Assistant speech: " + speech));
        String raw = languageModelGateway.complete(messages);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject candidate = parsed.getAsJsonObject();
            JsonObject nonVerbal = candidate;
            if (candidate.has("nonVerbal") && candidate.get("nonVerbal").isJsonObject()) {
                nonVerbal = candidate.getAsJsonObject("nonVerbal");
            }
            JsonObject normalized = nonVerbal.deepCopy();
            String gesture = null;
            if (normalized.has("gesture") && !normalized.get("gesture").isJsonNull()) {
                gesture = normalizeGestureLabel(normalized.get("gesture").getAsString());
            }
            if (gesture == null) {
                gesture = "NONE";
            }
            normalized.addProperty("gesture", gesture);
            removeUnsupportedLocomotion(normalized);
            return normalized;
        } catch (Exception ignored) {
            return null;
        }
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

    private static void removeUnsupportedLocomotion(JsonObject nonVerbal) {
        if (!nonVerbal.has("motion") || !nonVerbal.get("motion").isJsonObject()) {
            return;
        }
        JsonObject motion = nonVerbal.getAsJsonObject("motion");
        motion.remove("move");
        motion.remove("turn");
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

