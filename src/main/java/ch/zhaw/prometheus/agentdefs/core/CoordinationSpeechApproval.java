package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.util.UUID;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;

/** Projects existing speaking occasions; owns no task transitions or contribution budget. */
final class CoordinationSpeechApproval {
    private CoordinationSpeechApproval() {}
    static boolean enabled(Storage s) { return s.containsKey(LiveSpeechRequest.ENABLED) && s.get(LiveSpeechRequest.ENABLED).getAsBoolean(); }
    static LiveSpeechRequest request(Storage s, ExternalSpeech owner) {
        if (!enabled(s) || owner == null) return null;
        var primary = primary(s, owner);
        var sensory = sensory(s, owner);
        // A fresh ambient occasion may follow an expired reply, but never displace a current one.
        return sensory != null && (primary == null || !sensory.created().isBefore(primary.expiresAt())) ? sensory : primary;
    }
    static void scopeSensory(Storage s, ExternalSpeech owner, Instant now) {
        if (!enabled(s)) return;
        var p = s.get(SensoryRemarks.PENDING).getAsJsonObject().deepCopy();
        p.addProperty("created", now.toString()); p.addProperty("session", owner.sessionId().toString());
        p.addProperty("epoch", owner.epoch().toString()); p.addProperty("revision", TaskMemory.text(s, TaskMemory.REVISION, "0"));
        s.put(SensoryRemarks.PENDING, p);
    }
    private static LiveSpeechRequest sensory(Storage s, ExternalSpeech owner) {
        if (!s.containsKey(SensoryRemarks.PENDING) || TaskMemory.active(s)
                || java.util.Set.of("PAUSED", "FINISHING", "UNCERTAIN").contains(TaskMemory.phase(s))) return null;
        var p = s.get(SensoryRemarks.PENDING).getAsJsonObject();
        if (!p.has("created") || !p.has("session") || !p.has("epoch") || !p.has("revision")
                || !owner.sessionId().toString().equals(p.get("session").getAsString())
                || !owner.epoch().toString().equals(p.get("epoch").getAsString())
                || !TaskMemory.text(s, TaskMemory.REVISION, "0").equals(p.get("revision").getAsString())) return null;
        var expires = Instant.parse(p.get("expires").getAsString());
        return new LiveSpeechRequest(UUID.fromString(p.get("id").getAsString()), owner, p.get("revision").getAsInt(),
                Instant.parse(p.get("created").getAsString()), expires, p.get("facts").getAsString(), expires);
    }
    private static LiveSpeechRequest primary(Storage s, ExternalSpeech owner) {
        String key = s.containsKey(CoordinationSupport.LIVE_HELP)
                && !s.get(CoordinationSupport.LIVE_HELP).getAsJsonObject().has("consumed")
                ? CoordinationSupport.LIVE_HELP : CoreTaskVoice.RESPONSE;
        if (!s.containsKey(key)) return null;
        var v = s.get(key).getAsJsonObject();
        if (!v.has("id") || v.has("consumed") || !v.has("session")
                || !owner.sessionId().toString().equals(v.get("session").getAsString())
                || !owner.epoch().toString().equals(v.get("epoch").getAsString())
                || !TaskMemory.text(s, TaskMemory.REVISION, "0").equals(v.get("revision").getAsString())) return null;
        boolean help = key.equals(CoordinationSupport.LIVE_HELP);
        Instant created = Instant.parse(v.get(help ? "at" : "created").getAsString());
        String facts = help ? v.get("explanation").getAsString() : v.get("facts").getAsString();
        if (!help && v.has("proposalReview")) facts += "\nProposal background, NOT permission to perform it: "
                + CoordinationMemory.agreement(s, CoordinationMemory.DRAFT).json();
        if (!help && s.containsKey(TaskResponse.KEY) && v.get("id").equals(s.get(TaskResponse.KEY).getAsJsonObject().get("id")))
            facts = s.get(TaskResponse.KEY).getAsJsonObject().get("facts").getAsString();
        return new LiveSpeechRequest(UUID.fromString(v.get("id").getAsString()), owner, v.get("revision").getAsInt(),
                created, help ? created.plusSeconds(30) : Instant.parse(v.get("expires").getAsString()), facts);
    }
}
