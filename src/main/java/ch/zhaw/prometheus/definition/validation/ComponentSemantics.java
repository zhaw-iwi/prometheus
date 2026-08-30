package ch.zhaw.prometheus.definition.validation;

import java.util.List;
import java.util.Set;

public record ComponentSemantics(
        Set<String> consumedObservations,
        Set<String> emittedBehaviourModalities,
        List<ComponentStorageUse> storageUses,
        List<ComponentReference> resourceReferences,
        List<ComponentReference> stateReferences) {

    private static final ComponentSemantics NONE = new ComponentSemantics(Set.of(), Set.of(), List.of(), List.of(),
            List.of());

    public ComponentSemantics {
        consumedObservations = consumedObservations == null ? Set.of() : Set.copyOf(consumedObservations);
        emittedBehaviourModalities = emittedBehaviourModalities == null ? Set.of()
                : Set.copyOf(emittedBehaviourModalities);
        storageUses = storageUses == null ? List.of() : List.copyOf(storageUses);
        resourceReferences = resourceReferences == null ? List.of() : List.copyOf(resourceReferences);
        stateReferences = stateReferences == null ? List.of() : List.copyOf(stateReferences);
    }

    public static ComponentSemantics none() {
        return NONE;
    }
}
