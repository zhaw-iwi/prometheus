package ch.zhaw.prometheus.repositories;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ch.zhaw.prometheus.model.event.LiveTranscriptReceipt;

public interface LiveTranscriptReceiptRepository extends JpaRepository<LiveTranscriptReceipt, UUID> {
    Optional<LiveTranscriptReceipt> findBySessionIdAndProviderEventId(UUID sessionId, String providerEventId);
    List<LiveTranscriptReceipt> findAllBySessionIdAndProviderEventIdIn(UUID sessionId, List<String> providerEventIds);

    @Modifying
    @Query("update LiveTranscriptReceipt r set r.segmentId = :segmentId "
            + "where r.sessionId = :sessionId and r.epoch = :epoch and r.speaker = :speaker "
            + "and r.providerEventId in :providerEventIds and r.segmentId is null")
    int claimForSegment(UUID sessionId, UUID epoch, String speaker, List<String> providerEventIds, UUID segmentId);
}
