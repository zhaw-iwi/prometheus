package ch.zhaw.prometheus.application.study;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyFormRepository extends JpaRepository<StudyForm, UUID> {
    List<StudyForm> findByAttemptIdOrderByCreatedAt(UUID attemptId);
    Optional<StudyForm> findByAttemptIdAndKind(UUID attemptId, String kind);
}
