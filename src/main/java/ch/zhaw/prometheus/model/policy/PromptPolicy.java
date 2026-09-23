package ch.zhaw.prometheus.model.policy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.logging.LatencyTrace;
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
        return this.producePlan(messages, assembler, languageModelGateway);
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
        return this.producePlan(messages, assembler, languageModelGateway);
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

    private BehaviourPlan producePlan(List<PromptMessage> messages, PromptMessageAssembler assembler,
            LanguageModelGateway gateway) {
        boolean planConfigured = this.nonVerbalPlanPrompt != null && !this.nonVerbalPlanPrompt.isBlank();
        boolean gestureConfigured = this.nonVerbalGesturePrompt != null && !this.nonVerbalGesturePrompt.isBlank();
        String instructions = planConfigured ? this.nonVerbalPlanPrompt
                : gestureConfigured ? this.nonVerbalGesturePrompt : null;
        String resolvedInstructions = assembler.resolveSystemPrompt(instructions);
        return LatencyTrace.measure("behaviour_plan", () -> BehaviourPlanInference.generate(
                messages, resolvedInstructions, !planConfigured && gestureConfigured, gateway));
    }

    public ch.zhaw.prometheus.spi.InferenceRequest responseRequest(EventHistory events, PromptMessageAssembler assembler) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) return null;
        boolean plan = nonVerbalPlanPrompt != null && !nonVerbalPlanPrompt.isBlank();
        boolean gesture = nonVerbalGesturePrompt != null && !nonVerbalGesturePrompt.isBlank();
        String instructions = plan ? nonVerbalPlanPrompt : gesture ? nonVerbalGesturePrompt : null;
        return BehaviourPlanInference.request(assembler.compose(events, prompt),
                assembler.resolveSystemPrompt(instructions), !plan && gesture);
    }

    @Override
    public boolean decide(EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway languageModelGateway) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) {
            return false;
        }
        List<PromptMessage> messages = decisionMessages(events, assembler);
        return languageModelGateway.decide(messages);
    }

    public List<PromptMessage> decisionMessages(EventHistory events, PromptMessageAssembler assembler) {
        return assembler.composeCondensed(events, resolvePrompt(), LanguageModelGateway.REMINDER_DECISION);
    }

    public ch.zhaw.prometheus.spi.InferenceRequest actionRequest(EventHistory events,
            PromptMessageAssembler assembler, boolean summary) {
        String prompt = resolvePrompt();
        if (prompt.isEmpty()) return null;
        return new ch.zhaw.prometheus.spi.InferenceRequest(summary
                ? ch.zhaw.prometheus.spi.InferencePurpose.SUMMARY : ch.zhaw.prometheus.spi.InferencePurpose.EXTRACTION,
                assembler.composeCondensed(events, prompt, summary ? LanguageModelGateway.REMINDER_SUMMARISATION
                        : LanguageModelGateway.REMINDER_EXTRACTION), ch.zhaw.prometheus.spi.InferenceRequest.Output.JSON);
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

}
