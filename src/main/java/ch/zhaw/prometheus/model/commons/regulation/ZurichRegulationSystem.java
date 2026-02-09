package ch.zhaw.prometheus.model.commons.regulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.regulation.ModulationBundle;
import ch.zhaw.prometheus.model.regulation.RegulationContext;
import ch.zhaw.prometheus.model.regulation.RegulationEffect;
import ch.zhaw.prometheus.model.regulation.RegulationPolicy;
import ch.zhaw.prometheus.model.regulation.RegulationResult;
import ch.zhaw.prometheus.model.regulation.RegulationSystem;

public class ZurichRegulationSystem implements RegulationSystem {
    public static final String VAR_DEPENDENCY = "dependency";
    public static final String VAR_ENTERPRISE = "enterprise";
    public static final String VAR_AUTONOMY = "autonomy";

    private final Map<String, Double> variables;
    private final Map<String, Double> decayByVariable;
    private final Map<String, Double> minimumByVariable;
    private final Map<String, Double> maximumByVariable;
    private final double opportunityThreshold;
    private boolean opportunityArmed;
    private final List<RegulationPolicy> policies;

    public ZurichRegulationSystem() {
        this(0.0d, 0.0d, 0.0d, 0.05d, 0.20d, 0.30d, 0.70d);
    }

    public ZurichRegulationSystem(double dependency, double enterprise, double autonomy,
            double decayPerTick, double dependencyDeltaPerTick, double dependencyReliefOnUserUtterance,
            double opportunityThreshold) {
        this.variables = new LinkedHashMap<>();
        this.decayByVariable = new LinkedHashMap<>();
        this.minimumByVariable = new LinkedHashMap<>();
        this.maximumByVariable = new LinkedHashMap<>();

        defineVariable(VAR_DEPENDENCY, dependency, 0.0d, -1.0d, 1.0d);
        defineVariable(VAR_ENTERPRISE, enterprise, Math.max(0.0d, decayPerTick), -1.0d, 1.0d);
        defineVariable(VAR_AUTONOMY, autonomy, Math.max(0.0d, decayPerTick), -1.0d, 1.0d);

        this.opportunityThreshold = Math.max(0.0d, opportunityThreshold);
        this.opportunityArmed = false;
        this.policies = List.of(
                new TickRegulationPolicy(dependencyDeltaPerTick),
                new UserUtteranceRegulationPolicy(dependencyReliefOnUserUtterance));
    }

    @Override
    public RegulationResult update(RegulationContext context) {
        if (context == null || context.triggerEvent() == null) {
            return RegulationResult.none();
        }

        for (RegulationPolicy policy : this.policies) {
            RegulationEffect effect = policy.evaluate(context);
            if (effect == null) {
                continue;
            }
            this.applyEffect(effect);
        }
        this.applyDecay();

        List<Event> internal = new ArrayList<>();
        boolean aboveThreshold = this.getVariable(VAR_DEPENDENCY) >= this.opportunityThreshold;
        if (aboveThreshold && !this.opportunityArmed) {
            internal.add(Event.system(Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY,
                    "regulation opportunity: affiliation support", null, context.triggerEvent().getStateName()));
            this.opportunityArmed = true;
        } else if (!aboveThreshold) {
            this.opportunityArmed = false;
        }

        return new RegulationResult(this.toModulation(), internal);
    }

    public double getVariable(String variable) {
        if (variable == null) {
            return 0.0d;
        }
        return this.variables.getOrDefault(variable, 0.0d);
    }

    public Map<String, Double> getVariables() {
        return Map.copyOf(this.variables);
    }

    private void defineVariable(String variable, double initial, double decay, double min, double max) {
        String key = variable == null ? "" : variable.trim();
        if (key.isBlank()) {
            throw new IllegalArgumentException("variable name must not be blank");
        }
        this.minimumByVariable.put(key, min);
        this.maximumByVariable.put(key, max);
        this.decayByVariable.put(key, Math.max(0.0d, decay));
        this.variables.put(key, clampFor(key, initial));
    }

    private void applyEffect(RegulationEffect effect) {
        if (effect.deltas().isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : effect.deltas().entrySet()) {
            String variable = entry.getKey();
            if (variable == null || variable.isBlank()) {
                continue;
            }
            if (!this.variables.containsKey(variable)) {
                continue;
            }
            double delta = entry.getValue() == null ? 0.0d : entry.getValue();
            this.variables.put(variable, clampFor(variable, this.variables.get(variable) + delta));
        }
    }

    private void applyDecay() {
        for (Map.Entry<String, Double> entry : this.variables.entrySet()) {
            String variable = entry.getKey();
            double decay = this.decayByVariable.getOrDefault(variable, 0.0d);
            if (decay <= 0.0d) {
                continue;
            }
            this.variables.put(variable, clampFor(variable, decayTowardZero(entry.getValue(), decay)));
        }
    }

    private ModulationBundle toModulation() {
        double dependency = this.getVariable(VAR_DEPENDENCY);
        double enterprise = this.getVariable(VAR_ENTERPRISE);
        double autonomy = this.getVariable(VAR_AUTONOMY);
        double affiliation = positive(dependency);
        double aggression = positive(enterprise);
        double exploration = positive(autonomy);
        double supplication = positive(-dependency);
        double avoidance = positive(-autonomy);
        return new ModulationBundle(aggression, supplication, exploration, avoidance, affiliation);
    }

    private double clampFor(String variable, double value) {
        double min = this.minimumByVariable.getOrDefault(variable, -1.0d);
        double max = this.maximumByVariable.getOrDefault(variable, 1.0d);
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static double decayTowardZero(double value, double delta) {
        if (value > 0.0d) {
            return Math.max(0.0d, value - delta);
        }
        if (value < 0.0d) {
            return Math.min(0.0d, value + delta);
        }
        return 0.0d;
    }

    private static double positive(double value) {
        return Math.max(0.0d, value);
    }

    private static final class TickRegulationPolicy implements RegulationPolicy {
        private final double dependencyDeltaPerTick;

        private TickRegulationPolicy(double dependencyDeltaPerTick) {
            this.dependencyDeltaPerTick = dependencyDeltaPerTick;
        }

        @Override
        public RegulationEffect evaluate(RegulationContext context) {
            if (!Event.TYPE_SYSTEM_TICK.equals(context.triggerEvent().getType())) {
                return RegulationEffect.none();
            }
            return RegulationEffect.single(VAR_DEPENDENCY, this.dependencyDeltaPerTick, 1.0d, List.of("tick"));
        }
    }

    private static final class UserUtteranceRegulationPolicy implements RegulationPolicy {
        private final double dependencyReliefOnUserUtterance;

        private UserUtteranceRegulationPolicy(double dependencyReliefOnUserUtterance) {
            this.dependencyReliefOnUserUtterance = dependencyReliefOnUserUtterance;
        }

        @Override
        public RegulationEffect evaluate(RegulationContext context) {
            if (!Event.TYPE_USER_UTTERANCE.equals(context.triggerEvent().getType())) {
                return RegulationEffect.none();
            }
            return RegulationEffect.single(VAR_DEPENDENCY, -this.dependencyReliefOnUserUtterance, 1.0d,
                    List.of("user_utterance"));
        }
    }
}
