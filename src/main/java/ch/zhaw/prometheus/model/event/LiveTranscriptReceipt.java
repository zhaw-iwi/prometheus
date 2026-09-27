package ch.zhaw.prometheus.model.event;

import java.time.Instant;
import java.util.UUID;
import ch.zhaw.prometheus.model.Agent;
import jakarta.persistence.*;

/** Durable provider receipt, separate from an accepted agent observation. Never contains audio. */
@Entity
@Table(name = "live_transcript_receipt", uniqueConstraints = @UniqueConstraint(name = "uk_live_receipt", columnNames = {"session_id", "provider_event_id"}))
public class LiveTranscriptReceipt {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Agent agent;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    private UUID epoch;
    @Column(name = "provider_event_id", length = 200, nullable = false) private String providerEventId;
    @Column(length = 12) private String speaker;
    private Long startMs, endMs;
    private Instant receivedAt;
    private long arrivalSequence;
    @Column(length = 3000) private String transcript;
    private UUID segmentId;
    protected LiveTranscriptReceipt() {}
    public LiveTranscriptReceipt(Agent agent, UUID sessionId, UUID epoch, String providerEventId, String speaker,
            Long startMs, Long endMs, Instant receivedAt, long sequence, String transcript) {
        this.id = UUID.nameUUIDFromBytes((sessionId + ":" + providerEventId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.agent = agent; this.sessionId = sessionId; this.epoch = epoch; this.providerEventId = providerEventId;
        this.speaker = speaker; this.startMs = startMs; this.endMs = endMs; this.receivedAt = receivedAt;
        this.arrivalSequence = sequence; this.transcript = transcript;
    }
    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getEpoch() { return epoch; }
    public String getProviderEventId() { return providerEventId; }
    public String getSpeaker() { return speaker; }
    public Long getStartMs() { return startMs; }
    public Long getEndMs() { return endMs; }
    public Instant getReceivedAt() { return receivedAt; }
    public long getArrivalSequence() { return arrivalSequence; }
    public String getTranscript() { return transcript; }
    public UUID getSegmentId() { return segmentId; }
    public void assign(UUID segment) { if (segmentId != null && !segmentId.equals(segment)) throw new IllegalStateException("Receipt already assigned"); segmentId = segment; }
}
