package ch.zhaw.prometheus.model.interaction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.google.gson.Gson;

import ch.zhaw.prometheus.model.event.Event;

public class AgentInteractionProfile {
    private static final Gson GSON = new Gson();

    public static final String OBS_USER_UTTERANCE = Event.TYPE_USER_UTTERANCE;
    public static final String OBS_FACE_EMOTION = Event.TYPE_FACE_EMOTION;
    public static final String OBS_HUMAN_PRESENCE = Event.TYPE_HUMAN_PRESENCE;
    public static final String OBS_SOCIAL_GROUPING = Event.TYPE_SOCIAL_GROUPING;
    public static final String OBS_SOCIAL_SITUATION_CHANGE = Event.TYPE_SOCIAL_SITUATION_CHANGE;
    public static final String OBS_HAND_SIGN = Event.TYPE_HAND_SIGN;

    public static final String MODALITY_SPEECH = "speech";
    public static final String MODALITY_NONVERBAL_GESTURE = "nonVerbal.gesture";
    public static final String MODALITY_NONVERBAL_FACIAL_EXPRESSION = "nonVerbal.facialExpression";
    public static final String MODALITY_NONVERBAL_GAZE = "nonVerbal.gaze";
    public static final String MODALITY_MOTION_HAND_SIGN = "motion.handSign";
    public static final String MODALITY_DISPLAY = "display";

    public static final String TAG_GIGI_TDSR = "demo.gigi.tdsr";
    public static final String TAG_GIGI_GUESSING_GAME = "demo.gigi.guessing_game";
    public static final String TAG_GIGI_SOCIAL_CONTEXT = "demo.gigi.social_context";
    public static final String TAG_GIGI_RPS = "demo.gigi.rps";

    private List<String> supportedObservations;
    private List<String> supportedBehaviourModalities;
    private List<String> profileTags;

    protected AgentInteractionProfile() {
    }

    private AgentInteractionProfile(List<String> supportedObservations, List<String> supportedBehaviourModalities,
            List<String> profileTags) {
        this.supportedObservations = normalize(supportedObservations);
        this.supportedBehaviourModalities = normalize(supportedBehaviourModalities);
        this.profileTags = normalize(profileTags);
    }

    public static AgentInteractionProfile empty() {
        return new AgentInteractionProfile(List.of(), List.of(), List.of());
    }

    public static AgentInteractionProfile of(List<String> supportedObservations,
            List<String> supportedBehaviourModalities, List<String> profileTags) {
        return new AgentInteractionProfile(supportedObservations, supportedBehaviourModalities, profileTags);
    }

    public List<String> getSupportedObservations() {
        return normalize(this.supportedObservations);
    }

    public List<String> getSupportedBehaviourModalities() {
        return normalize(this.supportedBehaviourModalities);
    }

    public List<String> getProfileTags() {
        return normalize(this.profileTags);
    }

    public boolean supportsObservation(String observation) {
        return contains(this.supportedObservations, observation);
    }

    public boolean supportsBehaviourModality(String modality) {
        return contains(this.supportedBehaviourModalities, modality);
    }

    public String toJson() {
        return GSON.toJson(new AgentInteractionProfile(
                this.supportedObservations,
                this.supportedBehaviourModalities,
                this.profileTags));
    }

    public static AgentInteractionProfile fromJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        AgentInteractionProfile profile = GSON.fromJson(json, AgentInteractionProfile.class);
        if (profile == null) {
            return empty();
        }
        return new AgentInteractionProfile(profile.supportedObservations,
                profile.supportedBehaviourModalities,
                profile.profileTags);
    }

    private static boolean contains(List<String> values, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return normalize(values).contains(value.trim());
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}
