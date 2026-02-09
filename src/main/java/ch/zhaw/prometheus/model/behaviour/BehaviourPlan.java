package ch.zhaw.prometheus.model.behaviour;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class BehaviourPlan {
    private static final Gson GSON = new Gson();

    private String speech;
    private JsonElement nonVerbal;
    private JsonElement motion;
    private JsonElement display;

    public BehaviourPlan() {
    }

    public BehaviourPlan(String speech, JsonElement nonVerbal, JsonElement motion, JsonElement display) {
        this.speech = speech;
        this.nonVerbal = nonVerbal;
        this.motion = motion;
        this.display = display;
    }

    public static BehaviourPlan speechOnly(String speech) {
        return new BehaviourPlan(speech, null, null, null);
    }

    public String getSpeech() {
        return this.speech;
    }

    public void setSpeech(String speech) {
        this.speech = speech;
    }

    public JsonElement getNonVerbal() {
        return this.nonVerbal;
    }

    public void setNonVerbal(JsonElement nonVerbal) {
        this.nonVerbal = nonVerbal;
    }

    public JsonElement getMotion() {
        return this.motion;
    }

    public void setMotion(JsonElement motion) {
        this.motion = motion;
    }

    public JsonElement getDisplay() {
        return this.display;
    }

    public void setDisplay(JsonElement display) {
        this.display = display;
    }

    public boolean isEmpty() {
        return (this.speech == null || this.speech.isBlank())
                && this.nonVerbal == null
                && this.motion == null
                && this.display == null;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static BehaviourPlan fromJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        return GSON.fromJson(payload, BehaviourPlan.class);
    }

    public JsonObject toJsonObject() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }
}
