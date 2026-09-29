package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ch.zhaw.prometheus.application.AccessCodeAdminService;
import ch.zhaw.prometheus.controllers.views.AccessCodeView;
import ch.zhaw.prometheus.logging.*;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

/** Provider waits and the atomic visibility boundary, through actual HTTP and MySQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "prometheus.runtime.tick.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-timeout=1500"})
class ScopedCreationConcurrencyIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccessCodeAdminService admin;
    @Autowired AccessCodeRepository codes;
    @Autowired AgentRepository agents;
    @Autowired HikariDataSource pool;
    @MockitoBean LanguageModelGateway provider;
    @MockitoSpyBean AccessCodeAgentRepository links;
    @MockitoSpyBean AgentBehaviourBroadcaster behaviour;
    @MockitoSpyBean AgentMonitorBroadcaster monitor;
    private static final String TYPE = "core.facial_expression_sensitivity";
    private static final String PLAN = "{\"speech\":\"Ready\",\"nonVerbal\":{\"gesture\":\"NONE\"}}";
    private HttpClient client;
    private AccessCodeView access;
    private final List<String> created = new ArrayList<>();

    @BeforeEach void setup() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        access = admin.createAccessCode(UUID.randomUUID().toString().replace("-", "").substring(0, 5), true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(TYPE));
        when(provider.infer(any())).thenReturn(PLAN);
    }
    @AfterEach void cleanup() throws Exception {
        // Restore access for HTTP cleanup after revocation tests.
        admin.updateAccessCodeEnabled(access.getId(), true);
        for (String id : created) send("DELETE", "/demo/agents/" + id, null);
        codes.deleteById(access.getId());
        client.close();
    }
    @Test void eightSimultaneousProviderWaitsLeaveFourConnectionsAvailableAndCommitVisibleAgents() throws Exception {
        var entered = new CountDownLatch(8); var release = new CountDownLatch(1);
        blockProvider(entered, release);
        var pending = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        try {
            for (int i = 0; i < 8; i++) pending.add(create());
            assertTrue(entered.await(10, TimeUnit.SECONDS), "all eight requests reach the provider with only four DB connections");
            idle();
            unpublished();
            assertEquals(200, send("POST", "/demo/session", Map.of("accessCode", access.getCode())).statusCode());
        } finally { release.countDown(); }
        for (var future : pending) {
            var response = future.get(15, TimeUnit.SECONDS);
            assertEquals(201, response.statusCode(), response.body());
            created.add(json.readTree(response.body()).get("id").asText());
        }
        assertEquals(8, links.findByAccessCodeId(access.getId()).size());
        verify(behaviour, times(8)).publish(any(), any());
        verify(monitor, times(8)).publish(any());
        idle();
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void revocationDuringGenerationRejectsCreationWithoutOrphans(boolean disableCode) throws Exception {
        long before = agents.count();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        blockProvider(entered, release);
        var pending = create();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            idle();
            if (disableCode) admin.updateAccessCodeEnabled(access.getId(), false);
            else admin.replaceAllowedAgentTypes(access.getId(), List.of());
        } finally { release.countDown(); }
        assertEquals(disableCode ? 401 : 403, pending.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(before, agents.count());
        assertTrue(links.findByAccessCodeId(access.getId()).isEmpty());
        unpublished();
    }
    @Test void replacementAccessCodeCannotInheritAnInFlightCreation() throws Exception {
        long before = agents.count();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        blockProvider(entered, release);
        var pending = create();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            String code = access.getCode();
            codes.deleteById(access.getId());
            access = admin.createAccessCode(code, true);
            admin.replaceAllowedAgentTypes(access.getId(), List.of(TYPE));
        } finally { release.countDown(); }
        assertEquals(401, pending.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(before, agents.count());
        assertTrue(links.findByAccessCodeId(access.getId()).isEmpty());
        unpublished();
    }
    @Test void providerFailureDoesNotPersistOrPublish() throws Exception {
        long before = agents.count();
        when(provider.infer(any())).thenThrow(new IllegalStateException("synthetic provider failure"));
        assertEquals(500, create().get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(before, agents.count());
        assertTrue(links.findByAccessCodeId(access.getId()).isEmpty());
        unpublished(); idle();
    }
    @Test void associationFailureRollsBackAgentAndSuppressesBothPublications() throws Exception {
        long before = agents.count();
        doThrow(new DataIntegrityViolationException("synthetic association failure")).when(links).saveAndFlush(any());
        assertEquals(500, create().get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(before, agents.count(), "agent save and access association must roll back together");
        assertTrue(links.findByAccessCodeId(access.getId()).isEmpty());
        unpublished(); idle();
    }
    private void blockProvider(CountDownLatch entered, CountDownLatch release) {
        when(provider.infer(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "provider must run outside the creation transaction");
            entered.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("test provider deadline");
            return PLAN;
        });
    }
    private void unpublished() {
        verify(behaviour, never()).publish(any(), any());
        verify(monitor, never()).publish(any());
    }
    private void idle() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            assertEquals(0, pool.getHikariPoolMXBean().getThreadsAwaitingConnection());
        });
    }
    private HttpRequest request(String method, String path, Object body) throws Exception {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(25))
                .header(ScopedDemoController.ACCESS_CODE_HEADER, access.getCode()).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }
    private CompletableFuture<HttpResponse<String>> create() throws Exception {
        return client.sendAsync(request("POST", "/demo/agents", Map.of("agentDefinitionKey", TYPE)), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        return client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
    }
}
