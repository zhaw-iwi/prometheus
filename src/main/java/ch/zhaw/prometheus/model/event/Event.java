package ch.zhaw.prometheus.model.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import ch.zhaw.prometheus.spi.GsonExclude;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;

@Entity
public class Event {
    public static final String TYPE_USER_UTTERANCE = "obs.user_utterance";
    public static final String TYPE_FACE_EMOTION = "obs.emotion.face";
    public static final String TYPE_HUMAN_PRESENCE = "obs.human.presence";
    public static final String TYPE_SOCIAL_GROUPING = "obs.social.grouping";
    public static final String TYPE_ASSISTANT_UTTERANCE = "resp.assistant_utterance";
    public static final String TYPE_ASSISTANT_BEHAVIOUR_PLAN = "resp.behaviour_plan";
    public static final String TYPE_SYSTEM_PROMPT = "sys.prompt";
    public static final String TYPE_SYSTEM_TICK = "sys.tick";
    public static final String TYPE_INTERNAL_REGULATION_OPPORTUNITY = "int.regulation.opportunity";
    public static final String TYPE_INTERNAL_REGULATION_INTERRUPT_SOFT = "int.regulation.interrupt.soft";
    public static final String TYPE_INTERNAL_REGULATION_INTERRUPT_HARD = "int.regulation.interrupt.hard";
    public static final String KIND_OBSERVATION = "observation";
    public static final String KIND_RESPONSE = "response";
    public static final String KIND_SYSTEM = "system";
    public static final String ACTOR_USER = "user";
    public static final String ACTOR_ASSISTANT = "assistant";
    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue
    @GsonExclude
    private UUID id;

    protected Event() {

    }

    @GsonExclude
    private String type;
    private String actor;
    private String kind;
    @Column(length = 4096)
    private String payload;

    @CreationTimestamp
    @Column(name = "createdDate", nullable = false, updatable = false)
    private Instant createdDate;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_state_path", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "path_index")
    @Column(name = "state_name")
    @GsonExclude
    private List<String> statePath;
    @ManyToOne
    @JoinColumn(name = "event_history_id")
    @GsonExclude
    private EventHistory eventHistory;

    public Event(String type, String actor, String kind, String payload) {
        this.type = type;
        this.actor = actor;
        this.kind = kind;
        this.payload = payload;
        this.statePath = new ArrayList<>();
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

    public String getType() {
        return this.type;
    }

    public String getActor() {
        return this.actor;
    }

    public String getKind() {
        return this.kind;
    }

    public String getPayload() {
        return this.payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public List<String> getStatePath() {
        if (this.statePath == null) {
            this.statePath = new ArrayList<>();
        }
        return List.copyOf(this.statePath);
    }

    public void setStatePath(List<String> statePath) {
        if (statePath == null) {
            this.statePath = new ArrayList<>();
            return;
        }
        this.statePath = new ArrayList<>(statePath);
    }

    public Event withStatePath(String... stateNames) {
        if (stateNames == null) {
            this.statePath = new ArrayList<>();
            return this;
        }
        this.statePath = new ArrayList<>(Arrays.asList(stateNames));
        return this;
    }

    public void setEventHistory(EventHistory eventHistory) {
        this.eventHistory = eventHistory;
    }

    @Override
    public String toString() {
        return "{type=\"" + type + "\", actor=\"" + actor + "\", kind=\"" + kind
                + "\", payload=\"" + payload + "\", statePath=\""
                + statePath + "\"}";
    }
}

