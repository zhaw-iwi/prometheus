package ch.zhaw.prometheus.repositories;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ch.zhaw.prometheus.model.event.LiveTranscriptSegment;

public interface LiveTranscriptSegmentRepository extends JpaRepository<LiveTranscriptSegment, UUID> {
    List<LiveTranscriptSegment> findTop100ByAgent_IdAndSessionIdOrderByRecordedAtDesc(UUID agentId, UUID sessionId);
    List<LiveTranscriptSegment> findTop20ByAgent_IdAndEpochAndSpeakerAndStatusOrderByRecordedAtDesc(UUID agentId, UUID epoch, String speaker, String status);
}
