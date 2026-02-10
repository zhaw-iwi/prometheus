package ch.zhaw.prometheus.model.regulation;

import java.util.LinkedHashMap;
import java.util.Map;

public class ZurichRegulationConfig {
    private Map<String, Double> variables;
    private Map<String, Double> decayByVariable;
    private Map<String, Double> minimumByVariable;
    private Map<String, Double> maximumByVariable;
    private double opportunityThreshold;
    private boolean opportunityArmed;
    private double dependencyDeltaPerTick;
    private double dependencyReliefOnUserUtterance;

    protected ZurichRegulationConfig() {
    }

    public ZurichRegulationConfig(Map<String, Double> variables, Map<String, Double> decayByVariable,
            Map<String, Double> minimumByVariable, Map<String, Double> maximumByVariable, double opportunityThreshold,
            boolean opportunityArmed, double dependencyDeltaPerTick, double dependencyReliefOnUserUtterance) {
        this.variables = variables == null ? Map.of() : Map.copyOf(variables);
        this.decayByVariable = decayByVariable == null ? Map.of() : Map.copyOf(decayByVariable);
        this.minimumByVariable = minimumByVariable == null ? Map.of() : Map.copyOf(minimumByVariable);
        this.maximumByVariable = maximumByVariable == null ? Map.of() : Map.copyOf(maximumByVariable);
        this.opportunityThreshold = opportunityThreshold;
        this.opportunityArmed = opportunityArmed;
        this.dependencyDeltaPerTick = dependencyDeltaPerTick;
        this.dependencyReliefOnUserUtterance = dependencyReliefOnUserUtterance;
    }

    public Map<String, Double> getVariables() {
        return this.variables == null ? Map.of() : Map.copyOf(this.variables);
    }

    public Map<String, Double> getDecayByVariable() {
        return this.decayByVariable == null ? Map.of() : Map.copyOf(this.decayByVariable);
    }

    public Map<String, Double> getMinimumByVariable() {
        return this.minimumByVariable == null ? Map.of() : Map.copyOf(this.minimumByVariable);
    }

    public Map<String, Double> getMaximumByVariable() {
        return this.maximumByVariable == null ? Map.of() : Map.copyOf(this.maximumByVariable);
    }

    public double getOpportunityThreshold() {
        return this.opportunityThreshold;
    }

    public boolean isOpportunityArmed() {
        return this.opportunityArmed;
    }

    public double getDependencyDeltaPerTick() {
        return this.dependencyDeltaPerTick;
    }

    public double getDependencyReliefOnUserUtterance() {
        return this.dependencyReliefOnUserUtterance;
    }

    public static ZurichRegulationConfig defaults() {
        Map<String, Double> variables = new LinkedHashMap<>();
        variables.put("dependency", 0.0d);
        variables.put("enterprise", 0.0d);
        variables.put("autonomy", 0.0d);

        Map<String, Double> decay = new LinkedHashMap<>();
        decay.put("dependency", 0.0d);
        decay.put("enterprise", 0.05d);
        decay.put("autonomy", 0.05d);

        Map<String, Double> minimum = new LinkedHashMap<>();
        minimum.put("dependency", -1.0d);
        minimum.put("enterprise", -1.0d);
        minimum.put("autonomy", -1.0d);

        Map<String, Double> maximum = new LinkedHashMap<>();
        maximum.put("dependency", 1.0d);
        maximum.put("enterprise", 1.0d);
        maximum.put("autonomy", 1.0d);

        return new ZurichRegulationConfig(variables, decay, minimum, maximum, 0.70d, false, 0.20d, 0.30d);
    }
}
