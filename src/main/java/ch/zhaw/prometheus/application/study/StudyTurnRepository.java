package ch.zhaw.prometheus.application.study;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyTurnRepository extends JpaRepository<StudyTurn,UUID> {
    long countByAttemptIdAndInputType(UUID attemptId,String inputType);

    Optional<StudyTurn> findByAttemptIdAndRequestId(UUID attempt, UUID request);
    List<StudyTurn> findByAttemptIdOrderByReceivedAt(UUID attempt);
    long countByAttemptId(UUID attempt);
}
