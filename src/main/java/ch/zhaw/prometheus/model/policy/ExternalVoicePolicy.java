package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.EventHistory;

/**
 * Explicit extension contract for application-owned Live policies. Implementations keep
 * executable rules in the backend and explicitly select facts available to Live.
 */
public interface ExternalVoicePolicy {
    boolean supportsLiveState(State state);
    String voiceInstructions();
    /** Opt in to Live answering from current application facts and quiet state updates. */
    default LiveActivityContext liveActivityContext() { return null; }
    /** Spoken waiting acknowledgments are permitted by default; false requests silent waiting in guidance only. */
    default boolean livePendingAcknowledgements() { return true; }
    /** Application-specific waiting guidance; prompt permission only, never evidence that work ran. */
    default String livePendingGuidance() {
        return livePendingAcknowledgements() ? "You may acknowledge immediately while work is pending."
                : "Wait silently while work is pending; no waiting remarks or question echoes.";
    }
    /** Session-fixed quiet interval for backend user-turn capture, not provider speech generation. */
    default long liveUserSilenceMs() { return 800; }
    /** Session-fixed assistant sentence assembly; completion still needs observed quiet and current authority. */
    default long liveAssistantSilenceMs() { return 800; }
    /** Requires an approval-capable player; default policies keep direct native playback. */
    default boolean liveSpeechApprovalRequired() { return false; }
    /** Select output-only synthesis for backend-authored replies; native audio stays inaudible. */
    default boolean liveCommittedSpeechOutput() { return false; }
    default LiveCommittedSpeech liveCommittedSpeech(ExternalSpeech owner) { return null; }
    /** Persist one synthesis claim before provider work. No retry after an uncertain result. */
    default boolean liveSpeechSynthesisClaimed(LiveCommittedSpeech speech, java.util.UUID synthesisId) { return false; }
    /** Immutable authority for checking a candidate; null means no speech is authorized. */
    default LiveSpeechRequest liveSpeechRequest(ExternalSpeech owner) { return null; }
    /** Reserve at most one unheard correction under current authority. Local storage only; null declines. */
    default java.util.UUID liveSpeechCorrection(LiveSpeechRequest request, String rejectedSegment, java.time.Instant now) { return null; }
    /** Persist an admitted release under the agent lock; return whether storage changed. */
    default boolean liveSpeechReleased(LiveSpeechRequest request, String segment) { return false; }
    /** Opt-in occupied-encounter grace for camera dropouts; Stop and access checks remain immediate. */
    default long liveDepartureGraceMs() { return 0; }
    /** Opt-in rejection of isolated input activity spikes for backend capture only. */
    default boolean liveFilterIsolatedInputActivity() { return false; }
    /** Opt-in admission only when this exact input is guaranteed not to change task/activity control. */
    default boolean liveNonMutatingAcknowledgement(String transcript) { return false; }
    /** Opt in to post-admission acknowledgement classification through ordinary application control. */
    default boolean liveContextualAcknowledgements() { return false; }
    /**
     * Post-admission evidence for this exact input, classified by ordinary application control.
     * Called under the turn lock; local validation only, never inference or I/O.
     * The caller also rechecks the original playback cue, deadline and speech owner.
     */
    default boolean liveNonMutatingAcknowledgement(ch.zhaw.prometheus.model.event.Event input, ExternalSpeech owner) { return false; }
    /** Resolve a short control reply from current application facts and completed session dialogue.
     * Ingress still requires a preceding, non-overlapping assistant segment from the current owner.
     * This does not authorize an action: ordinary acknowledgement/actions validate it again.
     */
    default boolean liveResolvedControlReply(String transcript, EventHistory history, ExternalSpeech owner) { return false; }
    /** Called in the durable native-speech transaction. Bounded local validation/storage only; no inference or I/O. */
    default boolean liveSpeechCaptured(ch.zhaw.prometheus.model.event.Event speech, ExternalSpeech owner,
            java.time.Instant started, java.time.Instant now) {
        return liveSpeechCaptured(speech, owner, started, now, java.util.List.of());
    }
    default boolean liveSpeechCaptured(ch.zhaw.prometheus.model.event.Event speech, ExternalSpeech owner,
            java.time.Instant started, java.time.Instant now, java.util.List<LiveSpeechPart> parts) { return false; }
    /** Internal event admitted only after a current application cue receives a scoped playback acknowledgement. */
    default String livePlaybackCompletionEventType() { return null; }
    /** Encounter boundary for dialogue/person sensing; selected fresh weather retains its own source expiry. */
    default int liveHistoryStartIndex() { return 0; }
    /** Defer unsolicited sensory facts to Live only; backend history and task matching remain available. */
    default boolean liveObservationContextEnabled() { return true; }
    default String liveOpeningInstructions() { return ""; }
    /** Optional internal event, acknowledged once on close through ordinary state transitions. */
    default String liveSessionEndEventType() { return null; }
    /** Optional ordinary transition before a fresh session claim, for unresolved work from a lost owner. */
    default String liveSessionClaimEventType() { return null; }
    /** Called under the agent turn lock, before claiming a new voice session. */
    default void beginLiveConversation(EventHistory history) {}
}
