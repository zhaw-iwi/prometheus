package ch.zhaw.prometheus.application;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.LiveTranscriptReceipt;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.*;

/** Browser playback evidence opens only the currently expected application phase, once. */
@Service
public class LiveSpeechCueService {
    public record Request(UUID cueId, UUID roundId, String outcome, List<String> receiptIds) {
        public Request {
            if (cueId == null || roundId == null || outcome == null || !Set.of("complete", "interrupted", "timeout", "unavailable").contains(outcome)
                    || receiptIds == null || receiptIds.size() > 32 || new HashSet<>(receiptIds).size() != receiptIds.size()
                    || receiptIds.stream().anyMatch(id -> id == null || !id.matches("[A-Za-z0-9_:.\\-]{1,200}")))
                throw new IllegalArgumentException("Invalid speech cue");
            receiptIds = List.copyOf(receiptIds);
        }
    }
    private final AgentApplicationService turns;
    private final AgentRepository agents;
    private final ExternalSpeechOwnership ownership;
    private final LiveTranscriptReceiptRepository receipts;
    private final TransactionTemplate transaction;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public LiveSpeechCueService(AgentApplicationService turns, AgentRepository agents, ExternalSpeechOwnership ownership,
            LiveTranscriptReceiptRepository receipts, PlatformTransactionManager transactions) {
        this(turns, agents, ownership, receipts, transactions, Clock.systemUTC());
    }
    LiveSpeechCueService(AgentApplicationService turns, AgentRepository agents, ExternalSpeechOwnership ownership,
            LiveTranscriptReceiptRepository receipts, PlatformTransactionManager transactions, Clock clock) {
        this.turns = turns; this.agents = agents; this.ownership = ownership; this.receipts = receipts;
        this.clock = clock;
        transaction = new TransactionTemplate(transactions);
    }
    public boolean accept(UUID id, ExternalSpeech owner, Request request) {
        return turns.serialized(id, () -> Boolean.TRUE.equals(transaction.execute(status -> {
            var agent = agents.findById(id).orElse(null);
            if (agent == null || !owner.equals(ownership.current(agent)) || !ownership.taskWorkAllowed(id, owner)
                    || !(agent.getCurrentState().ownPolicy() instanceof ExternalVoicePolicy policy)) return false;
            var cue = policy.liveSpeechCue();
            if (cue == null || !cue.id().equals(request.cueId()) || !cue.roundId().equals(request.roundId())
                    || policy.liveSpeechCueEventType() == null) return false;
            Instant now = clock.instant();
            boolean verified = !"complete".equals(request.outcome()) || verified(owner, request, cue, now);
            // Missing sideband receipts cannot be retried into a later phase. Recover without opening capture.
            dispatch(id, policy, cue, request.outcome(), verified && now.isBefore(cue.deadline()));
            return verified && now.isBefore(cue.deadline());
        })));
    }
    /** Called with the managed agent inside Live context refresh's serialized transaction. */
    void expire(ch.zhaw.prometheus.model.Agent agent, ExternalSpeech owner) {
        if (!(agent.getCurrentState().ownPolicy() instanceof ExternalVoicePolicy policy)
                || policy.liveSpeechCueEventType() == null) return;
        var cue = policy.liveSpeechCue();
        if (cue == null || clock.instant().isBefore(cue.deadline()) || !owner.equals(ownership.current(agent))
                || !ownership.taskWorkAllowed(agent.getId(), owner)) return;
        dispatch(agent.getId(), policy, cue, "timeout", false);
    }
    private void dispatch(UUID id, ExternalVoicePolicy policy, LiveSpeechCue cue, String outcome, boolean verified) {
        var payload = new JsonObject(); payload.addProperty("cueId", cue.id().toString());
        payload.addProperty("roundId", cue.roundId().toString()); payload.addProperty("outcome", outcome);
        payload.addProperty("verified", verified);
        turns.acknowledge(id, new EventRequest(policy.liveSpeechCueEventType(), "system", Event.KIND_SYSTEM, payload.toString()), OutputProfile.FULL_PLAN);
    }
    private boolean verified(ExternalSpeech owner, Request request, LiveSpeechCue cue, Instant now) {
        if (request.receiptIds().isEmpty() || !now.isBefore(cue.deadline())) return false;
        var evidence = receipts.findAllBySessionIdAndProviderEventIdIn(owner.sessionId(), request.receiptIds());
        if (evidence.size() != request.receiptIds().size() || evidence.stream().anyMatch(receipt ->
                !owner.epoch().equals(receipt.getEpoch()) || !"ASSISTANT".equals(receipt.getSpeaker())
                || receipt.getReceivedAt().toEpochMilli() < cue.startedAt().toEpochMilli() || receipt.getReceivedAt().isAfter(now)
                || receipt.getStartMs() == null || receipt.getEndMs() == null || receipt.getEndMs() < receipt.getStartMs()
                || receipt.getStartMs() < cue.minimumStartMs())) return false;
        var ordered = evidence.stream().sorted(Comparator.comparingLong(LiveTranscriptReceipt::getArrivalSequence)).toList();
        String text = ordered.stream().map(LiveTranscriptReceipt::getTranscript).collect(java.util.stream.Collectors.joining());
        return countdown(text) && !receipts.existsBySessionIdAndEpochAndSpeakerAndReceivedAtGreaterThanEqual(
                owner.sessionId(), owner.epoch(), "USER", cue.startedAt());
    }
    static boolean countdown(String text) {
        if (text.length() > 4000 || text.matches("(?s).*[\"“”„«»].*")) return false;
        // Match the final countdown, allowing earlier mentions in the invitation.
        return java.util.regex.Pattern.compile("\\bschere[\\s,;.!…–-]+stein[\\s,;.!…–-]+papier\\b[\\s.!?…]*$")
                .matcher(text.toLowerCase(Locale.ROOT)).find();
    }
}
