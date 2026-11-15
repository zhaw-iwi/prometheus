package ch.zhaw.prometheus.application.study;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

/** Procedural research state; never an agent prompt or task state. */
@Entity
@Table(name = "study_attempt")
public class StudyAttempt {
    public enum Mode { SYNTHETIC, PILOT, MAIN }
    public enum Stage { INFORMATION, PRE, INTENTION, PRIVATE, COORDINATION, REVISION, TRIAL, ASSESSMENT,
        COMPLETE, WITHDRAWN, REVOKED, REPLACED }
    @Id @GeneratedValue UUID id;
    @Version long version;
    @Column(nullable=false, unique=true, length=64) String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) Mode mode;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) Stage stage;
    @Column(nullable=false, length=24) String scenario;
    @Column(nullable=false, length=64) String materialsVersion;
    @Column(length=100) String consentVersion;
    Instant consentedAt;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    UUID predecessorId;
    @Column(unique=true) UUID agentId;
    long turnVersion;
    UUID pendingRequest;
    @Column(length=24) String finishReason;
    protected StudyAttempt() {}
    StudyAttempt(String tokenHash, Mode mode, String scenario, String materialsVersion, UUID predecessorId) {
        this.tokenHash=tokenHash; this.mode=mode; this.scenario=scenario;
        this.materialsVersion=materialsVersion; this.predecessorId=predecessorId;
        stage=Stage.INFORMATION; createdAt=updatedAt=Instant.now();
    }
    boolean closed() { return switch(stage) {
        case COMPLETE, WITHDRAWN, REVOKED, REPLACED -> true; default -> false;
    }; }
    void advance(Stage next) { stage=next; updatedAt=Instant.now(); }
}
