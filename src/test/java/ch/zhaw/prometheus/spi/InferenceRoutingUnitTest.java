package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class InferenceRoutingUnitTest {
    @Test void productionProfileLoadsHerokuPurposeRoutesWithoutStartingDatabaseOrProvider() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(OpenAIProperties.class)
                .withPropertyValues("spring.profiles.active=prod", "openai.key=offline-test")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var properties = context.getBean(OpenAIProperties.class);
                    assertEquals("gpt-5.2", properties.getModel());
                    for (InferencePurpose purpose : InferencePurpose.values()) {
                        var route = InferenceRouting.resolve(properties, purpose);
                        assertEquals(purpose == InferencePurpose.BEHAVIOUR || purpose == InferencePurpose.NONVERBAL
                                ? "gpt-5.6-sol" : "gpt-5.6-luna", route.model(), purpose.name());
                        assertEquals("none", route.effort(), purpose.name());
                        // Environment endpoint overrides remain authoritative for isolated test runs.
                        assertEquals(properties.getUrl(), route.url());
                    }
                });
    }

    @Test void springConfigurationBindsPurposeOverridesAndPreservesGlobalFallback() {
        var source = new MapConfigurationPropertySource(Map.of(
                "openai.openaivsazureopenai", "openai", "openai.model", "gpt-5.2",
                "openai.url", "http://localhost/chat", "openai.reasoning-effort", "none",
                "openai.routes.behaviour.model", "gpt-5.6-sol",
                "openai.routes.decision.model", "gpt-5.6-luna",
                "openai.routes.decision.max-completion-tokens", "256"));
        var properties = new Binder(source).bind("openai", OpenAIProperties.class).get();
        assertEquals("gpt-5.6-sol", InferenceRouting.resolve(properties, InferencePurpose.BEHAVIOUR).model());
        var decision = InferenceRouting.resolve(properties, InferencePurpose.DECISION);
        assertEquals("gpt-5.6-luna", decision.model());
        assertEquals("none", decision.effort());
        assertEquals(256, decision.maxCompletionTokens());
        assertEquals("gpt-5.2", InferenceRouting.resolve(properties, InferencePurpose.EXTRACTION).model());
        assertFalse(decision.samplingParameters());
    }

    @Test void invalidEffortDeadlineAndAzureDeploymentCombinationsFailBeforeDispatch() {
        var properties = new OpenAIProperties();
        properties.setModel("gpt-4o-mini");
        properties.setUrl("http://localhost/chat");
        properties.setReasoningEffort("none");
        assertThrows(IllegalArgumentException.class, () -> InferenceRouting.resolve(properties, InferencePurpose.DECISION));
        properties.setModel("gpt-5.6-luna");
        properties.setReasoningEffort("minimal");
        assertThrows(IllegalArgumentException.class, () -> InferenceRouting.resolve(properties, InferencePurpose.DECISION));
        properties.setReasoningEffort("none");
        properties.setRequestTimeoutMs(0);
        assertThrows(IllegalArgumentException.class, () -> InferenceRouting.resolve(properties, InferencePurpose.DECISION));
        properties.setRequestTimeoutMs(1000);
        properties.setOpenaivsazureopenai("azureopenai");
        var route = new OpenAIProperties.InferenceRoute();
        route.setModel("gpt-5.6-sol");
        properties.getRoutes().put(InferencePurpose.BEHAVIOUR, route);
        assertThrows(IllegalArgumentException.class, () -> InferenceRouting.resolve(properties, InferencePurpose.BEHAVIOUR));
        route.setUrl("http://localhost/deployments/sol/chat");
        assertEquals(route.getUrl(), InferenceRouting.resolve(properties, InferencePurpose.BEHAVIOUR).url());
        properties.getRoutes().clear(); properties.setModel(null); properties.setReasoningEffort(null);
        assertEquals("azure-deployment", InferenceRouting.resolve(properties, InferencePurpose.DECISION).model());
    }

    @Test void structuredValuesCannotMasqueradeAsSuccess() {
        for (String raw : new String[] { "TRUE", "yes", "true because yes", "null", "\"false\"", "" }) {
            assertThrows(IllegalStateException.class, () -> InferenceResult.bool(raw));
        }
        assertTrue(InferenceResult.bool(" true\n"));
        for (String raw : new String[] { "{key:1}", "{\"key\":1} trailing", "null", "", "```json\n{}\n```" }) {
            assertThrows(IllegalStateException.class, () -> InferenceResult.json(raw), raw);
        }
        assertEquals("value", InferenceResult.json("\"value\"").getAsString());
        assertTrue(InferenceResult.json("[]").isJsonArray());
    }
}
