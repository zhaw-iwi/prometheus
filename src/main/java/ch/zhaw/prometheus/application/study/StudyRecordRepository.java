package ch.zhaw.prometheus.application.study;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyRecordRepository extends JpaRepository<StudyRecord,UUID> {
    List<StudyRecord> findByAttemptIdOrderByRecordedAt(UUID attempt);
    Optional<StudyRecord> findByAttemptIdAndRecordKey(UUID attempt,String key);
    long countByAttemptIdAndKind(UUID attempt,String kind);
}
