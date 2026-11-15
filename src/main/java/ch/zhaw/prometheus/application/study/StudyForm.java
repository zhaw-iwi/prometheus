package ch.zhaw.prometheus.application.study;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="study_form", uniqueConstraints=@UniqueConstraint(columnNames={"attempt_id", "form_kind"}))
public class StudyForm {
    @Id @GeneratedValue UUID id;
    @Column(name="attempt_id", nullable=false) UUID attemptId;
    @Column(name="form_kind", nullable=false, length=24) String kind;
    @Column(nullable=false, columnDefinition="text") String answers;
    @Column(nullable=false, length=64) String materialsVersion;
    @Column(nullable=false) Instant createdAt;
    protected StudyForm() {}
    StudyForm(StudyAttempt attempt, String kind, String answers) {
        attemptId=attempt.id; this.kind=kind; this.answers=answers;
        materialsVersion=attempt.materialsVersion; createdAt=Instant.now();
    }
}
