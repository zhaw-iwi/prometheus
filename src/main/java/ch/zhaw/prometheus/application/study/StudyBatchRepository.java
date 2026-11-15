package ch.zhaw.prometheus.application.study;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StudyBatchRepository extends JpaRepository<StudyBatch,UUID> {
    @Modifying @Query(value="insert ignore into study_batch (id,version,requested) values (:id,0,0)",nativeQuery=true)
    void ensure(@Param("id")UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from StudyBatch b where b.id=:id")
    Optional<StudyBatch> lock(@Param("id")UUID id);
}
