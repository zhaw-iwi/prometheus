package ch.zhaw.prometheus.model.event;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import ch.zhaw.prometheus.spi.GsonExclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Event {
    public static final String TYPE_USER_UTTERANCE = "obs.user_utterance";
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
    private String content;
    @Column(length = 4096)
    private String payload;

    @CreationTimestamp
    @Column(name = "createdDate", nullable = false, updatable = false)
    private Instant createdDate;
    @GsonExclude
    private String stateName;
    @ManyToOne
    @JoinColumn(name = "event_history_id")
    @GsonExclude
    private EventHistory eventHistory;

    public Event(String type, String actor, String kind, String content, String payload, String stateName) {
        this.type = type;
        this.actor = actor;
        this.kind = kind;
        this.content = content;
        this.payload = payload;
        this.stateName = stateName;
    }

    public static Event observation(String type, String actor, String content, String payload, String stateName) {
        return new Event(type, actor, KIND_OBSERVATION, content, payload, stateName);
    }

    public static Event response(String type, String actor, String content, String payload, String stateName) {
        return new Event(type, actor, KIND_RESPONSE, content, payload, stateName);
    }

    public static Event systemPrompt(String content, String stateName) {
        return new Event(TYPE_SYSTEM_PROMPT, ACTOR_SYSTEM, KIND_SYSTEM, content, null, stateName);
    }

    public static Event system(String type, String content, String payload, String stateName) {
        return new Event(type, ACTOR_SYSTEM, KIND_SYSTEM, content, payload, stateName);
    }

    public static Event systemTick(String stateName) {
        return system(TYPE_SYSTEM_TICK, "tick", null, stateName);
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

    public String getContent() {
        return this.content;
    }

    public String getPayload() {
        return this.payload;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getStateName() {
        return this.stateName;
    }

    public void setEventHistory(EventHistory eventHistory) {
        this.eventHistory = eventHistory;
    }

    @Override
    public String toString() {
        return "{type=\"" + type + "\", actor=\"" + actor + "\", kind=\"" + kind
                + "\", content=\"" + content + "\", payload=\"" + payload + "\", stateName=\""
                + stateName + "\"}";
    }
}
