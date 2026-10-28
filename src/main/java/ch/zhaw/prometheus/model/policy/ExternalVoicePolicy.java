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
    /** Opt-in occupied-encounter grace for camera dropouts; Stop and access checks remain immediate. */
    default long liveDepartureGraceMs() { return 0; }
    /** Opt-in rejection of isolated input activity spikes for backend capture only. */
    default boolean liveFilterIsolatedInputActivity() { return false; }
    /** Opt-in admission only when this exact input is guaranteed not to change task/activity control. */
    default boolean liveNonMutatingAcknowledgement(String transcript) { return false; }
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
    default String liveOpeningInstructions() { return ""; }
    /** Optional internal event, acknowledged once on close through ordinary state transitions. */
    default String liveSessionEndEventType() { return null; }
    /** Optional ordinary transition before a fresh session claim, for unresolved work from a lost owner. */
    default String liveSessionClaimEventType() { return null; }
    /** Called under the agent turn lock, before claiming a new voice session. */
    default void beginLiveConversation(EventHistory history) {}
}
