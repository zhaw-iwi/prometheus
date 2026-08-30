package ch.zhaw.prometheus.definition.component.builtin;

import java.util.List;

import ch.zhaw.prometheus.definition.compiled.CompiledStorageBinding;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.CompiledAction;

public record ExtractionActionComponent(
        String extractionPrompt,
        String targetStorageKey,
        ImmutableJson outputSchema,
        List<CompiledStorageBinding> storageBindings) implements CompiledAction {

    public ExtractionActionComponent {
        storageBindings = storageBindings == null ? List.of() : List.copyOf(storageBindings);
    }
}
