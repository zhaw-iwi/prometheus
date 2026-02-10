package ch.zhaw.prometheus.model.event;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

public class EventSelectorSpec {
    private static final Gson GSON = new Gson();

    public enum Kind {
        ANY,
        TYPE,
        ACTOR,
        KIND,
        STATE_NAME,
        AND,
        OR
    }

    private Kind kind;
    private List<String> values;
    private EventSelectorSpec left;
    private EventSelectorSpec right;

    protected EventSelectorSpec() {
        this.values = new ArrayList<>();
    }

    private EventSelectorSpec(Kind kind, List<String> values, EventSelectorSpec left, EventSelectorSpec right) {
        this.kind = kind;
        this.values = values == null ? List.of() : List.copyOf(values);
        this.left = left;
        this.right = right;
    }

    public static EventSelectorSpec any() {
        return new EventSelectorSpec(Kind.ANY, List.of(), null, null);
    }

    public static EventSelectorSpec type(String... types) {
        return new EventSelectorSpec(Kind.TYPE, asValues(types), null, null);
    }

    public static EventSelectorSpec actor(String... actors) {
        return new EventSelectorSpec(Kind.ACTOR, asValues(actors), null, null);
    }

    public static EventSelectorSpec kind(String... kinds) {
        return new EventSelectorSpec(Kind.KIND, asValues(kinds), null, null);
    }

    public static EventSelectorSpec stateName(String... stateNames) {
        return new EventSelectorSpec(Kind.STATE_NAME, asValues(stateNames), null, null);
    }

    public static EventSelectorSpec and(EventSelectorSpec left, EventSelectorSpec right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("left and right selector specs must not be null");
        }
        return new EventSelectorSpec(Kind.AND, List.of(), left, right);
    }

    public static EventSelectorSpec or(EventSelectorSpec left, EventSelectorSpec right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("left and right selector specs must not be null");
        }
        return new EventSelectorSpec(Kind.OR, List.of(), left, right);
    }

    public Kind getKind() {
        return this.kind;
    }

    public List<String> getValues() {
        return this.values == null ? List.of() : List.copyOf(this.values);
    }

    public EventSelectorSpec getLeft() {
        return this.left;
    }

    public EventSelectorSpec getRight() {
        return this.right;
    }

    public EventSelector toEventSelector() {
        if (this.kind == null) {
            throw new IllegalStateException("selector spec kind not set");
        }
        return switch (this.kind) {
            case ANY -> EventSelector.any();
            case TYPE -> EventSelector.type(requireValues("type"));
            case ACTOR -> EventSelector.actor(requireValues("actor"));
            case KIND -> EventSelector.kind(requireValues("kind"));
            case STATE_NAME -> EventSelector.stateName(requireValues("stateName"));
            case AND -> requireChild("left").toEventSelector().and(requireChild("right").toEventSelector());
            case OR -> requireChild("left").toEventSelector().or(requireChild("right").toEventSelector());
        };
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static EventSelectorSpec fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        EventSelectorSpec spec = GSON.fromJson(json, EventSelectorSpec.class);
        if (spec == null || spec.kind == null) {
            throw new IllegalStateException("invalid event selector spec json");
        }
        return spec;
    }

    private EventSelectorSpec requireChild(String label) {
        EventSelectorSpec child = "left".equals(label) ? this.left : this.right;
        if (child == null) {
            throw new IllegalStateException("selector spec " + this.kind + " requires child " + label);
        }
        return child;
    }

    private String[] requireValues(String label) {
        if (this.values == null || this.values.isEmpty()) {
            throw new IllegalStateException("selector spec " + this.kind + " requires " + label + " values");
        }
        return this.values.toArray(String[]::new);
    }

    private static List<String> asValues(String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("selector values must not be empty");
        }
        return List.of(values);
    }
}
