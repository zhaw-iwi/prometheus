package ch.zhaw.prometheus.model.snapshot;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ObservationSnapshot {
    private final Instant createdAt;
    private final int sourceEventCount;
    private final Map<String, Fact> facts;

    public ObservationSnapshot(int sourceEventCount, Collection<Fact> facts) {
        this.createdAt = Instant.now();
        this.sourceEventCount = Math.max(0, sourceEventCount);
        this.facts = new LinkedHashMap<>();
        if (facts != null) {
            for (Fact fact : facts) {
                if (fact != null) {
                    this.facts.put(fact.getKey(), fact);
                }
            }
        }
    }

    public static ObservationSnapshot empty() {
        return new ObservationSnapshot(0, java.util.List.of());
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public int getSourceEventCount() {
        return this.sourceEventCount;
    }

    public Map<String, Fact> getFacts() {
        return Map.copyOf(this.facts);
    }

    public Optional<Fact> getFact(String key) {
        return Optional.ofNullable(this.facts.get(key));
    }

    public boolean hasFact(String key) {
        return this.facts.containsKey(key);
    }

    public String getString(String key) {
        Fact fact = this.facts.get(key);
        if (fact == null || fact.getValue() == null) {
            return null;
        }
        return String.valueOf(fact.getValue());
    }

    public Integer getInteger(String key) {
        Number number = this.getNumber(key);
        if (number == null) {
            return null;
        }
        return number.intValue();
    }

    public Long getLong(String key) {
        Number number = this.getNumber(key);
        if (number == null) {
            return null;
        }
        return number.longValue();
    }

    public Double getDouble(String key) {
        Number number = this.getNumber(key);
        if (number == null) {
            return null;
        }
        return number.doubleValue();
    }

    public Boolean getBoolean(String key) {
        Fact fact = this.facts.get(key);
        if (fact == null || fact.getValue() == null) {
            return null;
        }
        if (fact.getValue() instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(fact.getValue()));
    }

    private Number getNumber(String key) {
        Fact fact = this.facts.get(key);
        if (fact == null || fact.getValue() == null) {
            return null;
        }
        if (fact.getValue() instanceof Number numberValue) {
            return numberValue;
        }
        try {
            return Double.parseDouble(String.valueOf(fact.getValue()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
