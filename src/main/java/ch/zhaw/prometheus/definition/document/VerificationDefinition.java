package ch.zhaw.prometheus.definition.document;

import java.util.List;

public record VerificationDefinition(List<VerificationScenario> scenarios) {
    public VerificationDefinition {
        scenarios = DocumentCollections.copyList(scenarios);
    }
}
