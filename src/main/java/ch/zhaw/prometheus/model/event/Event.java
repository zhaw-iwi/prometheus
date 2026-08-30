package ch.zhaw.prometheus.model.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;

/** Public event transport retained independently from runtime JSON persistence. */
public class Event {
    public static final String TYPE_USER_UTTERANCE = "obs.user_utterance";
    public static final String TYPE_FACE_EMOTION = "obs.emotion.face";
    public static final String TYPE_HUMAN_PRESENCE = "obs.human.presence";
    public static final String TYPE_SOCIAL_GROUPING = "obs.social.grouping";
    public static final String TYPE_SOCIAL_CONTEXT = "obs.social.context";
    public static final String TYPE_SOCIAL_SITUATION_CHANGE = "obs.social.situation_change";
    public static final String TYPE_HAND_SIGN = "obs.hand.sign";
    public static final String TYPE_WEATHER_CURRENT = "obs.weather.current";
    public static final String TYPE_WEATHER_FORECAST = "obs.weather.forecast";
    public static final String TYPE_ASSISTANT_BEHAVIOUR_PLAN = "resp.behaviour_plan";
    public static final String TYPE_SYSTEM_PROMPT = "sys.prompt";
    public static final String TYPE_SYSTEM_TICK = "sys.tick";
    public static final String KIND_OBSERVATION = "observation";
    public static final String KIND_RESPONSE = "response";
    public static final String KIND_SYSTEM = "system";
    public static final String ACTOR_USER = "user";
    public static final String ACTOR_ASSISTANT = "assistant";
    public static final String ACTOR_SYSTEM = "system";

    private final UUID id;
    private final Instant createdDate;
    private final String type;
    private final String actor;
    private final String kind;
    private String payload;
    private List<String> statePath;

    public Event(String type, String actor, String kind, String payload) {
        this(UUID.randomUUID(), Instant.now(), type, actor, kind, payload, List.of());
    }

    public Event(UUID id, Instant createdDate, String type, String actor, String kind, String payload,
            List<String> statePath) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.createdDate = createdDate == null ? Instant.now() : createdDate;
        this.type = type;
        this.actor = actor;
        this.kind = kind;
        this.payload = payload;
        this.statePath = statePath == null ? new ArrayList<>() : new ArrayList<>(statePath);
    }

    public static Event fromRuntime(RuntimeEvent event) {
        if (event == null) {
            return null;
        }
        return new Event(event.id(), event.createdAt(), event.type(), event.actor(), event.kind(), event.payload(),
                event.statePath());
    }

    public RuntimeEvent toRuntime() {
        return new RuntimeEvent(this.id, this.createdDate, this.type, this.actor, this.kind, this.payload,
                this.getStatePath());
    }

    public static Event observation(String type, String actor, String payload) {
        return new Event(type, actor, KIND_OBSERVATION, payload);
    }

    public static Event response(String type, String actor, String payload) {
        return new Event(type, actor, KIND_RESPONSE, payload);
    }

    public static Event systemPrompt(String payload) {
        return new Event(TYPE_SYSTEM_PROMPT, ACTOR_SYSTEM, KIND_SYSTEM, payload);
    }

    public static Event system(String type, String payload) {
        return new Event(type, ACTOR_SYSTEM, KIND_SYSTEM, payload);
    }

    public static Event systemTick() {
        return system(TYPE_SYSTEM_TICK, "tick");
    }

    public String getType() { return this.type; }

    @JsonIgnore
    public UUID getId() { return this.id; }

    public String getActor() { return this.actor; }
    public String getKind() { return this.kind; }
    public String getPayload() { return this.payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getCreatedDate() { return this.createdDate; }
    public List<String> getStatePath() { return List.copyOf(this.statePath); }

    public void setStatePath(List<String> statePath) {
        this.statePath = statePath == null ? new ArrayList<>() : new ArrayList<>(statePath);
    }

    public Event withStatePath(String... stateNames) {
        this.statePath = stateNames == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(stateNames));
        return this;
    }

    @Override
    public String toString() {
        return "{type=\"" + this.type + "\", actor=\"" + this.actor + "\", kind=\"" + this.kind
                + "\", payload=\"" + this.payload + "\", statePath=\"" + this.statePath + "\"}";
    }
}
