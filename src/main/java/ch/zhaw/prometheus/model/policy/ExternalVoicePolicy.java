package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.EventHistory;

/**
 * Explicit extension contract for application-owned Live policies. Implementations keep
 * executable rules and private task facts in the backend and expose only spoken guidance.
 */
public interface ExternalVoicePolicy {
    boolean supportsLiveState(State state);
    String voiceInstructions();
    /** Session-fixed quiet interval for backend user-turn capture, not provider speech generation. */
    default long liveUserSilenceMs() { return 800; }
    default int liveHistoryStartIndex() { return 0; }
    default String liveOpeningInstructions() { return ""; }
    /** Optional internal event, acknowledged once on close through ordinary state transitions. */
    default String liveSessionEndEventType() { return null; }
    /** Called under the agent turn lock, before claiming a new voice session. */
    default void beginLiveConversation(EventHistory history) {}
}
