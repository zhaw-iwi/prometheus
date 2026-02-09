package ch.zhaw.prometheus.model.regulation;

import java.util.ArrayList;
import java.util.List;

import ch.zhaw.prometheus.model.event.Event;

public class ZurichRegulationSystem implements RegulationSystem {
    private double dependency;
    private double enterprise;
    private double autonomy;

    private final double decayPerTick;
    private final double dependencyDeltaPerTick;
    private final double dependencyReliefOnUserUtterance;
    private final double opportunityThreshold;
    private boolean opportunityArmed;

    public ZurichRegulationSystem() {
        this(0.0d, 0.0d, 0.0d, 0.05d, 0.20d, 0.30d, 0.70d);
    }

    public ZurichRegulationSystem(double dependency, double enterprise, double autonomy,
            double decayPerTick, double dependencyDeltaPerTick, double dependencyReliefOnUserUtterance,
            double opportunityThreshold) {
        this.dependency = clamp(dependency);
        this.enterprise = clamp(enterprise);
        this.autonomy = clamp(autonomy);
        this.decayPerTick = Math.max(0.0d, decayPerTick);
        this.dependencyDeltaPerTick = dependencyDeltaPerTick;
        this.dependencyReliefOnUserUtterance = dependencyReliefOnUserUtterance;
        this.opportunityThreshold = Math.max(0.0d, opportunityThreshold);
        this.opportunityArmed = false;
    }

    @Override
    public RegulationResult update(RegulationContext context) {
        if (context == null || context.triggerEvent() == null) {
            return RegulationResult.none();
        }
        Event trigger = context.triggerEvent();
        String triggerType = trigger.getType() == null ? "" : trigger.getType();

        if (Event.TYPE_SYSTEM_TICK.equals(triggerType)) {
            this.applyTick();
        } else if (Event.TYPE_USER_UTTERANCE.equals(triggerType)) {
            this.applyUserUtterance();
        }

        List<Event> internal = new ArrayList<>();
        boolean aboveThreshold = this.dependency >= this.opportunityThreshold;
        if (aboveThreshold && !this.opportunityArmed) {
            internal.add(Event.system(Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY,
                    "regulation opportunity: affiliation support", null, trigger.getStateName()));
            this.opportunityArmed = true;
        } else if (!aboveThreshold) {
            this.opportunityArmed = false;
        }

        return new RegulationResult(this.toModulation(), internal);
    }

    public double getDependency() {
        return this.dependency;
    }

    public double getEnterprise() {
        return this.enterprise;
    }

    public double getAutonomy() {
        return this.autonomy;
    }

    private void applyTick() {
        this.dependency = clamp(this.dependency + this.dependencyDeltaPerTick);
        this.enterprise = decayTowardZero(this.enterprise, this.decayPerTick);
        this.autonomy = decayTowardZero(this.autonomy, this.decayPerTick);
    }

    private void applyUserUtterance() {
        this.dependency = clamp(this.dependency - this.dependencyReliefOnUserUtterance);
    }

    private ModulationBundle toModulation() {
        double affiliation = positive(this.dependency);
        double aggression = positive(this.enterprise);
        double exploration = positive(this.autonomy);
        double supplication = positive(-this.dependency);
        double avoidance = positive(-this.autonomy);
        return new ModulationBundle(aggression, supplication, exploration, avoidance, affiliation);
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

    private static double clamp(double value) {
        if (value < -1.0d) {
            return -1.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }
}
