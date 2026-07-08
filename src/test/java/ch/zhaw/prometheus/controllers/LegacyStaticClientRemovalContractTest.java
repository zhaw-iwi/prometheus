package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class LegacyStaticClientRemovalContractTest {

    @Test
    void legacyStaticClientAssetsAreRemoved() {
        for (Path path : List.of(
                Path.of("src/main/resources/public/index.html"),
                Path.of("src/main/resources/public/script.js"),
                Path.of("src/main/resources/public/monitor"),
                Path.of("src/main/resources/public/nonverbal"),
                Path.of("src/main/resources/public/realtime"),
                Path.of("src/main/resources/public/visual"))) {
            assertFalse(Files.exists(path), "legacy static client should not exist: " + path);
        }
    }

    @Test
    void currentStaticClientsRemainAvailable() {
        for (Path path : List.of(
                Path.of("src/main/resources/public/style.css"),
                Path.of("src/main/resources/public/apiworkbench/index.html"),
                Path.of("src/main/resources/public/apiworkbench/script.js"),
                Path.of("src/main/resources/public/apiworkbench/workbench.css"),
                Path.of("src/main/resources/public/valerian/index.html"),
                Path.of("src/main/resources/public/valerian/script.js"),
                Path.of("src/main/resources/public/valerian-admin/index.html"),
                Path.of("src/main/resources/public/valerian-admin/script.js"),
                Path.of("src/main/resources/public/multilateral/listen/index.html"),
                Path.of("src/main/resources/public/multilateral/listen/script.js"),
                Path.of("src/main/resources/public/multilateral/reports/index.html"),
                Path.of("src/main/resources/public/multilateral/reports/script.js"))) {
            assertTrue(Files.exists(path), "current static client should exist: " + path);
        }
    }
}
