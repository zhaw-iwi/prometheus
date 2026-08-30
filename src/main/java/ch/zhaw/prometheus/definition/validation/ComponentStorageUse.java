package ch.zhaw.prometheus.definition.validation;

import com.fasterxml.jackson.databind.JsonNode;

public record ComponentStorageUse(
        String key,
        ComponentStorageAccess access,
        JsonNode expectedValueSchema,
        String configPointer) {
}
