package ch.zhaw.prometheus.application;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class AccessCodePresetCatalog {
    public List<AccessCodePresetSpec> list() {
        return List.of();
    }
}
