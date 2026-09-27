package ch.zhaw.prometheus.repositories;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ch.zhaw.prometheus.model.event.LiveTranscriptReceipt;

public interface LiveTranscriptReceiptRepository extends JpaRepository<LiveTranscriptReceipt, UUID> {
    Optional<LiveTranscriptReceipt> findBySessionIdAndProviderEventId(UUID sessionId, String providerEventId);
}
