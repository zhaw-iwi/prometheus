package ch.zhaw.prometheus.model.event;

import java.time.Instant;
import java.util.UUID;
import ch.zhaw.prometheus.model.Agent;
import jakarta.persistence.*;

/** Committed capture outcome, including rejected/late input which must never be acknowledged again. */
@Entity
@Table(name = "live_transcript_segment")
public class LiveTranscriptSegment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Agent agent;
    private UUID sessionId, epoch, eventId;
    @Column(length = 12) private String speaker;
    @Column(length = 24) private String status;
    @Column(length = 120) private String reason;
    @Column(length = 3000) private String transcript;
    @Column(length = 3000) private String statePath;
    private Long startMs, endMs;
    private Instant firstReceivedAt, recordedAt;
    protected LiveTranscriptSegment() {}
    public LiveTranscriptSegment(UUID id, Agent agent, UUID sessionId, UUID epoch, String speaker, String transcript,
            Long startMs, Long endMs, Instant firstReceivedAt, String statePath) {
        this.id = id; this.agent = agent; this.sessionId = sessionId; this.epoch = epoch;
        this.speaker = speaker; this.transcript = transcript; this.startMs = startMs; this.endMs = endMs;
        this.firstReceivedAt = firstReceivedAt; this.statePath = statePath; this.recordedAt = Instant.now(); this.status = "PENDING";
    }
    public void finish(String status, String reason, UUID eventId) { this.status = status; this.reason = reason; this.eventId = eventId; }
    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getEpoch() { return epoch; }
    public String getSpeaker() { return speaker; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getTranscript() { return transcript; }
    public String getStatePath() { return statePath; }
    public UUID getEventId() { return eventId; }
    public Long getStartMs() { return startMs; }
    public Long getEndMs() { return endMs; }
    public Instant getFirstReceivedAt() { return firstReceivedAt; }
    public Instant getRecordedAt() { return recordedAt; }
}
