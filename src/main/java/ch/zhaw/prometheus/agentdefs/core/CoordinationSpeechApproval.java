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
    static void words(Storage s, String text) {
        if (!s.containsKey(CoreTaskVoice.RESPONSE) || text == null || text.isBlank()) return;
        if (text.length() > 3000) throw new IllegalArgumentException("Committed speech is too long");
        var value = s.get(CoreTaskVoice.RESPONSE).getAsJsonObject().deepCopy();
        value.addProperty("spokenText", text); s.put(CoreTaskVoice.RESPONSE, value);
    }
    private static String deliveryKey(Storage s, LiveSpeechRequest request) {
        for (String key : java.util.List.of(CoordinationSupport.LIVE_HELP, CoreTaskVoice.RESPONSE, SensoryRemarks.PENDING))
            if (s.containsKey(key) && s.get(key).getAsJsonObject().has("id")
                    && request.id().toString().equals(s.get(key).getAsJsonObject().get("id").getAsString())) return key;
        return null;
    }
    static LiveCommittedSpeech committed(Storage s, ExternalSpeech owner) {
        var request = request(s, owner);
        if (request == null) return null;
        String key = deliveryKey(s, request); if (key == null) return null;
        var value = s.get(key).getAsJsonObject();
        String field = key.equals(CoordinationSupport.LIVE_HELP) ? "explanation" : "spokenText";
        if (!value.has(field)) return null;
        return new LiveCommittedSpeech(request, value.get(field).getAsString(),
                value.has("synthesisId") ? UUID.fromString(value.get("synthesisId").getAsString()) : null);
    }
    static boolean claim(Storage s, LiveCommittedSpeech speech, UUID synthesis) {
        if (speech.synthesisId() != null || !speech.equals(committed(s, speech.request().owner()))) return false;
        String key = deliveryKey(s, speech.request());
        var value = s.get(key).getAsJsonObject().deepCopy();
        if (value.has("released")) return false;
        value.addProperty("synthesisId", synthesis.toString()); s.put(key, value); return true;
    }
    private static com.google.gson.JsonObject ordinary(Storage s, LiveSpeechRequest request) {
        if (!request.equals(request(s, request.owner())) || !s.containsKey(CoreTaskVoice.RESPONSE)) return null;
        var value = s.get(CoreTaskVoice.RESPONSE).getAsJsonObject();
        if (!request.id().toString().equals(value.get("id").getAsString()) || value.has("consumed")
                || s.containsKey(TaskResponse.KEY) && value.get("id").equals(s.get(TaskResponse.KEY).getAsJsonObject().get("id"))) return null;
        return value.deepCopy();
    }
    static UUID correct(Storage s, LiveSpeechRequest request, String rejectedSegment, Instant now) {
        var value = ordinary(s, request);
        if (value == null || value.has("correctionId") || value.has("released") || now.isBefore(request.created())
                || now.isAfter(request.expiresAt().minusSeconds(TaskResponse.MIN_CORRECTION_SECONDS))) return null;
        UUID correction = UUID.randomUUID();
        value.addProperty("correctionId", correction.toString()); value.addProperty("correctionAt", now.toString());
        value.addProperty("rejectedSegment", rejectedSegment); s.put(CoreTaskVoice.RESPONSE, value); return correction;
    }
    static boolean released(Storage s, LiveSpeechRequest request, String segment) {
        var committed = committed(s, request.owner());
        if (committed != null && request.equals(committed.request()) && committed.synthesisId() != null) {
            String key = deliveryKey(s, request); var value = s.get(key).getAsJsonObject().deepCopy();
            if (value.has("released") || !committed.synthesisId().toString().equals(segment)) return false;
            value.addProperty("released", true); value.addProperty("releasedSegment", segment); s.put(key, value); return true;
        }
        var value = ordinary(s, request);
        if (value == null || value.has("released")) return false;
        value.addProperty("released", true); value.addProperty("releasedSegment", segment);
        s.put(CoreTaskVoice.RESPONSE, value); return true;
    }
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
        var data = p.getAsJsonObject("data");
        if (data.has("expression")) p.addProperty("spokenText", "Your expression appears " + data.get("expression").getAsString()
                + "; that is only an estimate, not a claim about how you feel.");
        else if (data.has("facePresent")) p.addProperty("spokenText", "No face is currently detected.");
        else if (data.has("humanCount")) p.addProperty("spokenText", "The current observation shows " + data.get("humanCount").getAsInt()
                + " people visible. This does not establish who arrived."
                + (data.has("groupCount") ? " Detected groups: " + data.get("groupCount").getAsInt() + "." : ""));
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
                Instant.parse(p.get("created").getAsString()), expires, p.get("facts").getAsString(), expires, null, null, p.has("released"));
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
                created, help ? created.plusSeconds(30) : Instant.parse(v.get("expires").getAsString()), facts, created.plusSeconds(60),
                v.has("correctionId") ? UUID.fromString(v.get("correctionId").getAsString()) : null,
                v.has("correctionAt") ? Instant.parse(v.get("correctionAt").getAsString()) : null, v.has("released"),
                help || v.has("proposalReview") || v.has("supportOnly"), !help && v.has("supportOnly") ? facts : null);
    }
}
