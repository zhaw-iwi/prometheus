package ch.zhaw.prometheus.model.regulation;

import com.google.gson.Gson;

import ch.zhaw.prometheus.model.commons.regulation.ZurichRegulationSystem;

public class RegulationSystemSpec {
    private static final Gson GSON = new Gson();

    public enum Kind {
        NO_OP,
        ZURICH
    }

    private Kind kind;
    private ZurichRegulationConfig zurich;

    protected RegulationSystemSpec() {
    }

    private RegulationSystemSpec(Kind kind, ZurichRegulationConfig zurich) {
        this.kind = kind;
        this.zurich = zurich;
    }

    public static RegulationSystemSpec noOp() {
        return new RegulationSystemSpec(Kind.NO_OP, null);
    }

    public static RegulationSystemSpec zurich(ZurichRegulationConfig zurich) {
        if (zurich == null) {
            throw new IllegalArgumentException("zurich config must not be null");
        }
        return new RegulationSystemSpec(Kind.ZURICH, zurich);
    }

    public Kind getKind() {
        return this.kind;
    }

    public ZurichRegulationConfig getZurich() {
        return this.zurich;
    }

    public RegulationSystem toRegulationSystem() {
        if (this.kind == null) {
            return new NoOpRegulationSystem();
        }
        return switch (this.kind) {
            case NO_OP -> new NoOpRegulationSystem();
            case ZURICH -> ZurichRegulationSystem.fromConfig(this.zurich);
        };
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static RegulationSystemSpec fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        RegulationSystemSpec spec = GSON.fromJson(json, RegulationSystemSpec.class);
        if (spec == null || spec.kind == null) {
            throw new IllegalStateException("invalid regulation system spec json");
        }
        return spec;
    }
}
