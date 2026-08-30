package ch.zhaw.prometheus.definition.document;

import com.fasterxml.jackson.annotation.JsonInclude;

public record VerificationEvent(
        String type,
        @JsonInclude(JsonInclude.Include.NON_NULL) String actor,
        @JsonInclude(JsonInclude.Include.NON_NULL) String kind,
        String payload) {
}
