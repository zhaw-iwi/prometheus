package ch.zhaw.prometheus.spi.script;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

public class InteractionScript {
    private static final Gson GSON = new Gson();

    private String name;
    private List<Step> steps;
    private List<GatewayCall> gatewayCalls;

    public String getName() {
        return this.name;
    }

    public List<Step> getSteps() {
        if (this.steps == null) {
            return List.of();
        }
        return List.copyOf(this.steps);
    }

    public List<GatewayCall> getGatewayCalls() {
        if (this.gatewayCalls == null) {
            return List.of();
        }
        return List.copyOf(this.gatewayCalls);
    }

    public static InteractionScript fromJson(String json) {
        InteractionScript parsed = GSON.fromJson(json, InteractionScript.class);
        if (parsed == null) {
            throw new IllegalArgumentException("script JSON did not deserialize");
        }
        parsed.normalize();
        return parsed;
    }

    private void normalize() {
        this.name = this.name == null ? "" : this.name.trim();
        this.steps = this.steps == null ? List.of() : List.copyOf(this.steps);
        this.gatewayCalls = this.gatewayCalls == null ? List.of() : List.copyOf(this.gatewayCalls);
    }

    public List<Step> expectedBehaviourSteps() {
        List<Step> expected = new ArrayList<>();
        for (Step step : getSteps()) {
            if (step == null || step.getExpectedBehaviour() == null) {
                continue;
            }
            expected.add(step);
        }
        return List.copyOf(expected);
    }

    public static class Step {
        private String id;
        private String action;
        private ScriptEvent event;
        private BehaviourExpectation expectedBehaviour;
        private String expectedState;
        private List<StorageExpectation> expectedStorage;

        public String getId() {
            return this.id;
        }

        public String getAction() {
            return this.action;
        }

        public ScriptEvent getEvent() {
            return this.event;
        }

        public BehaviourExpectation getExpectedBehaviour() {
            return this.expectedBehaviour;
        }

        public String getExpectedState() {
            return this.expectedState;
        }

        public List<StorageExpectation> getExpectedStorage() {
            if (this.expectedStorage == null) {
                return List.of();
            }
            return List.copyOf(this.expectedStorage);
        }
    }

    public static class ScriptEvent {
        private String type;
        private String actor;
        private String kind;
        private String payload;

        public String getType() {
            return this.type;
        }

        public String getActor() {
            return this.actor;
        }

        public String getKind() {
            return this.kind;
        }

        public String getPayload() {
            return this.payload;
        }
    }

    public static class BehaviourExpectation {
        private String speech;
        private JsonElement nonVerbal;

        public String getSpeech() {
            return this.speech;
        }

        public JsonElement getNonVerbal() {
            return this.nonVerbal;
        }
    }

    public static class GatewayCall {
        private String method;
        private JsonElement value;

        public String getMethod() {
            return this.method;
        }

        public JsonElement getValue() {
            return this.value;
        }
    }

    public static class StorageExpectation {
        private String key;
        private String contains;

        public String getKey() {
            return this.key;
        }

        public String getContains() {
            return this.contains;
        }
    }
}
