package ch.zhaw.prometheus.definition.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LocalMysqlSmokeSafetyUnitTest {

    @Test
    void acceptsOnlyPrefixedSchemaDifferentFromNormalDatabase() {
        assertEquals("prometheus_designer_smoke_local",
                LocalMysqlSmokeSafety.requireDedicatedSchema("prometheus_designer_smoke_local", "prometheus"));
        assertThrows(IllegalStateException.class,
                () -> LocalMysqlSmokeSafety.requireDedicatedSchema("prometheus", "prometheus"));
        assertThrows(IllegalStateException.class,
                () -> LocalMysqlSmokeSafety.requireDedicatedSchema("designer_test", "prometheus"));
        assertThrows(IllegalStateException.class,
                () -> LocalMysqlSmokeSafety.requireDedicatedSchema("prometheus_designer_smoke_normal",
                        "PROMETHEUS_DESIGNER_SMOKE_NORMAL"));
    }
}
