package ch.zhaw.prometheus.definition.component.builtin;

import java.util.List;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.CompiledResource;

public record TypedChoicesResourceComponent(List<ImmutableJson> values) implements CompiledResource {
    public TypedChoicesResourceComponent {
        values = List.copyOf(values);
    }
}
