package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StaticRedirectController.class)
class ValerianDesignerStaticResourceContractTest {
    private static final Path BUILD = Path.of("target/generated-resources/public/valerian-design");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void springServesTheGeneratedDesignerIndex() throws Exception {
        this.mockMvc.perform(get("/valerian-design/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Valerian Designer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-testid=\"valerian-designer-root\"")));
    }

    @Test
    void productionBundleContainsTheSixStepAndAdminHeaderContractsWithoutCredentials() throws IOException {
        Path index = BUILD.resolve("index.html");
        Path assets = BUILD.resolve("assets");
        assertTrue(Files.isRegularFile(index), "Vite index must be generated before Java tests");
        assertTrue(Files.isDirectory(assets), "Vite assets must be generated before Java tests");

        String html = Files.readString(index);
        assertTrue(html.contains("/valerian-design/assets/"));

        String javascript;
        String css;
        try (Stream<Path> files = Files.list(assets)) {
            var generated = files.toList();
            Path script = generated.stream().filter(path -> path.toString().endsWith(".js")).findFirst()
                    .orElseThrow();
            Path stylesheet = generated.stream().filter(path -> path.toString().endsWith(".css")).findFirst()
                    .orElseThrow();
            javascript = Files.readString(script);
            css = Files.readString(stylesheet);
        }

        assertTrue(javascript.contains("X-Prometheus-Admin-Token"));
        assertTrue(javascript.contains("prometheus.valerianAdmin.adminToken"));
        for (String title : new String[] { "Brief", "Capabilities", "Interaction", "Data & outcome", "Try",
                "Review" }) {
            assertTrue(javascript.contains(title));
        }
        assertTrue(css.contains("--step-z"));
        assertTrue(css.contains("width<=767.98px"));

        String lower = javascript.toLowerCase();
        assertFalse(lower.contains("http://localhost"));
        assertFalse(lower.contains("https://localhost"));
        assertFalse(lower.contains("127.0.0.1"));
        assertFalse(lower.contains("preview-manual-token"));
        assertFalse(lower.contains("root-token"));
        assertFalse(lower.contains("openai.key"));
    }

    @Test
    void containerAndCiBuildTheVerifiedFrontendWithoutCopyingLocalSecrets() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String dockerignore = Files.readString(Path.of(".dockerignore"));
        String qualityWorkflow = Files.readString(Path.of(".github/workflows/quality.yml"));
        String deploymentWorkflow = Files.readString(Path.of(".github/workflows/deployment.yml"));

        assertTrue(dockerfile.contains("AS build"));
        assertTrue(dockerfile.contains("clean package -DskipTests"));
        assertTrue(dockerfile.contains("FROM eclipse-temurin:21-jre-alpine"));
        assertTrue(dockerfile.contains("USER prometheus"));
        assertTrue(dockerfile.contains("ENTRYPOINT [\"java\", \"-jar\""));
        assertFalse(dockerfile.contains("spring-boot:run"));
        assertTrue(dockerignore.lines().anyMatch("src/main/resources/application.properties"::equals));
        assertTrue(dockerignore.lines().anyMatch("!src/main/resources/db/migration/*.sql"::equals));
        assertTrue(dockerignore.lines().anyMatch("test-results"::equals));
        assertTrue(dockerignore.lines().anyMatch("playwright-report"::equals));
        assertTrue(qualityWorkflow.contains("clean test"));
        assertTrue(qualityWorkflow.contains("test:designer:visual"));
        assertTrue(qualityWorkflow.contains("docker build"));
        assertTrue(deploymentWorkflow.contains("clean test"));
    }
}
