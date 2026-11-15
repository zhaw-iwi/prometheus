package ch.zhaw.prometheus.application.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StudyAllocationRepository extends JpaRepository<StudyAllocation,String> {
    @Modifying @Query(value="insert ignore into study_allocation (id,version,allocation_index) values (:id,0,0)",nativeQuery=true)
    void ensure(@Param("id")String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from StudyAllocation a where a.id=:id")
    Optional<StudyAllocation> lock(@Param("id")String id);
}
