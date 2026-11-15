package ch.zhaw.prometheus.application.study;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StudyAttemptRepository extends JpaRepository<StudyAttempt, UUID> {
    Optional<StudyAttempt> findByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StudyAttempt a where a.tokenHash = :hash")
    Optional<StudyAttempt> lockByHash(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StudyAttempt a where a.id = :id")
    Optional<StudyAttempt> lockById(@Param("id") UUID id);
    org.springframework.data.domain.Page<StudyAttempt> findByModeOrderByCreatedAtDesc(StudyAttempt.Mode mode,org.springframework.data.domain.Pageable page);
    List<StudyAttempt> findByModeOrderByCreatedAt(StudyAttempt.Mode mode);
    @Query("select a.agentId from StudyAttempt a where a.agentId is not null")
    Set<UUID> agentIds();
}
