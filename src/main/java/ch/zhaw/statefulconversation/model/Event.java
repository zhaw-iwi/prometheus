package ch.zhaw.statefulconversation.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import ch.zhaw.statefulconversation.spi.GsonExclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Event {
    public static final String TYPE_USER_UTTERANCE = "obs.user_utterance";
    public static final String TYPE_ASSISTANT_UTTERANCE = "obs.assistant_utterance";
    public static final String TYPE_SYSTEM_PROMPT = "sys.prompt";

    @Id
    @GeneratedValue
    @GsonExclude
    private UUID id;

    protected Event() {

    }

    @GsonExclude
    private String type;
    private String role;
    @Column(length = 4096)
    private String content;

    @CreationTimestamp
    @Column(name = "createdDate", nullable = false, updatable = false)
    private Instant createdDate;
    @GsonExclude
    private String stateName;
    @ManyToOne
    @JoinColumn(name = "event_history_id")
    @GsonExclude
    private EventHistory eventHistory;

    public Event(String type, String role, String content, String stateName) {
        this.type = type;
        this.role = role;
        this.content = content;
        this.stateName = stateName;
    }

    public static Event userUtterance(String content, String stateName) {
        return new Event(TYPE_USER_UTTERANCE, "user", content, stateName);
    }

    public static Event assistantUtterance(String content, String stateName) {
        return new Event(TYPE_ASSISTANT_UTTERANCE, "assistant", content, stateName);
    }

    public static Event systemPrompt(String content, String stateName) {
        return new Event(TYPE_SYSTEM_PROMPT, "system", content, stateName);
    }

    public String getType() {
        return this.type;
    }

    public String getRole() {
        return this.role;
    }

    public String getContent() {
        return this.content;
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
        return "{type=\"" + type + "\", role=\"" + role + "\", content=\"" + content + "\", stateName=\""
                + stateName + "\"}";
    }
}
