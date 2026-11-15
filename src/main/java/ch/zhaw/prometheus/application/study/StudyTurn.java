package ch.zhaw.prometheus.application.study;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="study_turn",uniqueConstraints=@UniqueConstraint(columnNames={"attempt_id","request_id"}))
public class StudyTurn {
    public enum Status { PENDING, COMMITTED, REJECTED, UNCERTAIN }
    @Id @GeneratedValue UUID id;
    @Column(name="attempt_id",nullable=false) UUID attemptId;
    @Column(name="request_id",nullable=false) UUID requestId;
    @Column(nullable=false,length=2000) String input;
    long expectedTurn;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) Status status;
    @Column(nullable=false) Instant receivedAt;
    @Column(nullable=false) Instant deadline;
    Instant finishedAt;
    Long committedTurn;
    UUID inputEventId;
    UUID responseEventId;
    @Column(length=80) String responseType;
    @Column(columnDefinition="text") String responsePayload;
    @Column(length=80) String reason;
    protected StudyTurn() {}
    StudyTurn(StudyAttempt attempt, UUID request, long expected, String text, Instant now) {
        attemptId=attempt.id; requestId=request; expectedTurn=expected; input=text;
        status=Status.PENDING; receivedAt=now.truncatedTo(java.time.temporal.ChronoUnit.MICROS); deadline=receivedAt.plusSeconds(120);
    }
    static Instant now() { return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
}
