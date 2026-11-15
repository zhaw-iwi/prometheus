package ch.zhaw.prometheus.application.study;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

/** Idempotent operator provisioning; only attempt IDs are retained, never invitation secrets. */
@Entity @Table(name="study_batch")
public class StudyBatch {
    @Id UUID id;
    @Version long version;
    @Enumerated(EnumType.STRING) @Column(length=16) StudyAttempt.Mode mode;
    int requested;
    @Column(columnDefinition="text") String attemptIds;
    Instant createdAt;
    protected StudyBatch(){}
}
