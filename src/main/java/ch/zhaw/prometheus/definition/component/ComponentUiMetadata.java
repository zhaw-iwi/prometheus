package ch.zhaw.prometheus.definition.component;

import java.util.List;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;

public record ComponentUiMetadata(
        String label,
        String description,
        ImmutableJson defaultConfig,
        List<ImmutableJson> examples,
        ComponentAuthoringRole authoringRole,
        ComponentAuthoringExposure exposure,
        String capabilityGroup,
        String advancedReason) {

    public ComponentUiMetadata {
        examples = examples == null ? List.of() : List.copyOf(examples);
        if (label == null || label.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Component UI label and description must be nonblank");
        }
        if (defaultConfig == null || authoringRole == null || exposure == null) {
            throw new IllegalArgumentException("Component UI defaults, authoring role, and exposure are required");
        }
        if (exposure == ComponentAuthoringExposure.GUIDED) {
            if (capabilityGroup == null || capabilityGroup.isBlank()) {
                throw new IllegalArgumentException("Guided components require a capability group");
            }
            if (advancedReason != null) {
                throw new IllegalArgumentException("Guided components must not declare an advanced-only reason");
            }
        } else {
            if (advancedReason == null || advancedReason.isBlank()) {
                throw new IllegalArgumentException("Non-guided components require an advanced-only reason");
            }
            if (capabilityGroup != null) {
                throw new IllegalArgumentException("Non-guided components must not declare a capability group");
            }
        }
    }
}
