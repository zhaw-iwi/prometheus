package ch.zhaw.prometheus.controllers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.application.BehaviourGenerationOutcome;
import ch.zhaw.prometheus.application.DemoAccessDeniedException;
import ch.zhaw.prometheus.application.DemoAgentTypeForbiddenException;
import ch.zhaw.prometheus.application.RealtimeCallOrchestrationService;
import ch.zhaw.prometheus.application.RealtimeCallSettings;
import ch.zhaw.prometheus.application.ScopedDemoService;
import ch.zhaw.prometheus.controllers.dto.DemoAgentCreateRequest;
import ch.zhaw.prometheus.controllers.dto.DemoSessionRequest;
import ch.zhaw.prometheus.controllers.views.AdminAgentTypeView;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.BehaviourGenerateRequest;
import ch.zhaw.prometheus.controllers.views.DemoSessionView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.RealtimeCallView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.RealtimeCallInfo;

@RestController
public class ScopedDemoController {
    public static final String ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";

    private final ScopedDemoService demoService;
    private final RealtimeCallOrchestrationService realtimeCallService;

    public ScopedDemoController(ScopedDemoService demoService, RealtimeCallOrchestrationService realtimeCallService) {
        this.demoService = demoService;
        this.realtimeCallService = realtimeCallService;
    }

    @PostMapping("/demo/session")
    public ResponseEntity<DemoSessionView> session(@RequestBody(required = false) DemoSessionRequest request) {
        if (request == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(this.demoService.openSession(request.getAccessCode()), HttpStatus.OK);
    }

    @GetMapping("/demo/agent-types")
    public ResponseEntity<List<AdminAgentTypeView>> agentTypes(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode) {
        return new ResponseEntity<>(this.demoService.listAgentTypes(accessCode(headerAccessCode, queryAccessCode)),
                HttpStatus.OK);
    }

    @GetMapping("/demo/agents")
    public ResponseEntity<List<AgentInfoView>> agents(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode) {
        return new ResponseEntity<>(this.demoService.listAgents(accessCode(headerAccessCode, queryAccessCode)),
                HttpStatus.OK);
    }

    @PostMapping("/demo/agents")
    public ResponseEntity<AgentInfoView> createAgent(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @RequestBody(required = false) DemoAgentCreateRequest request) {
        if (request == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        AgentInfoView created = this.demoService.createAgent(accessCode(headerAccessCode, queryAccessCode),
                request.getAgentDefinitionKey());
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @DeleteMapping("/demo/agents/{agentId}")
    public ResponseEntity<Void> deleteAgent(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        boolean deleted = this.demoService.deleteAgent(accessCode(headerAccessCode, queryAccessCode), agentId);
        return new ResponseEntity<>(deleted ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    @GetMapping("/demo/agents/{agentId}/info")
    public ResponseEntity<AgentInfoView> info(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        return this.demoService.getAgentInfo(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/demo/agents/{agentId}/eventhistory")
    public ResponseEntity<List<Event>> eventHistory(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        return this.demoService.getAgentEventHistory(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/demo/agents/{agentId}/state")
    public ResponseEntity<AgentStateInfoView> state(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        return this.demoService.getAgentState(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/demo/agents/{agentId}/states")
    public ResponseEntity<List<String>> states(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        return this.demoService.getAgentStates(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/demo/agents/{agentId}/storage")
    public ResponseEntity<List<StorageEntryView>> storage(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        return this.demoService.getAgentStorage(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/demo/agents/{agentId}/start")
    public ResponseEntity<ResponseView> start(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        return this.demoService.start(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping("/demo/agents/{agentId}/reset")
    public ResponseEntity<ResponseView> reset(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        return this.demoService.reset(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/demo/agents/{agentId}/acknowledge")
    public ResponseEntity<ResponseView> acknowledge(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId,
            @RequestBody EventRequest request,
            @RequestParam(required = false) String profile) {
        return this.acknowledge(headerAccessCode, queryAccessCode, agentId, request, profile, false);
    }

    @PostMapping("/demo/agents/{agentId}/acknowledge-and-generate")
    public ResponseEntity<ResponseView> acknowledgeAndGenerate(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId,
            @RequestBody EventRequest request,
            @RequestParam(required = false) String profile) {
        return this.acknowledge(headerAccessCode, queryAccessCode, agentId, request, profile, true);
    }

    private ResponseEntity<ResponseView> acknowledge(String headerAccessCode, String queryAccessCode, UUID agentId,
            EventRequest request, String profile, boolean generateIfNoResponse) {
        OutputProfile outputProfile = OutputProfile.fromNullable(profile);
        if (outputProfile == null || !isValidEventRequest(request)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        String resolvedAccessCode = accessCode(headerAccessCode, queryAccessCode);
        Optional<ResponseView> response = generateIfNoResponse
                ? this.demoService.acknowledgeAndGenerate(resolvedAccessCode, agentId, request, outputProfile)
                : this.demoService.acknowledge(resolvedAccessCode, agentId, request, outputProfile);
        return response
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/demo/agents/{agentId}/behaviour/generate")
    public ResponseEntity<Void> generate(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable UUID agentId,
            @RequestBody(required = false) BehaviourGenerateRequest request) {
        OutputProfile outputProfile = OutputProfile.fromNullable(request == null ? null : request.getOutputProfile());
        if (outputProfile == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        BehaviourGenerationOutcome outcome = this.demoService.generate(accessCode(headerAccessCode, queryAccessCode),
                agentId, request == null ? null : request.getOmitModalities(), outputProfile);
        return switch (outcome) {
            case GENERATED -> new ResponseEntity<>(HttpStatus.OK);
            case NO_BEHAVIOUR_GENERATED -> new ResponseEntity<>(HttpStatus.CONFLICT);
            case AGENT_NOT_FOUND -> new ResponseEntity<>(HttpStatus.NOT_FOUND);
        };
    }

    @GetMapping(path = "/demo/agents/{agentId}/behaviour/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> behaviourStream(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable UUID agentId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) String lastEventIdParam) {
        String lastEventId = lastEventIdParam == null || lastEventIdParam.isBlank()
                ? lastEventIdHeader
                : lastEventIdParam;
        Optional<SseEmitter> emitter = this.demoService.subscribeBehaviour(accessCode(headerAccessCode,
                queryAccessCode), agentId, lastEventId);
        return emitter.map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/demo/agents/{agentId}/monitor/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> monitorStream(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable UUID agentId) {
        Optional<SseEmitter> emitter = this.demoService.subscribeMonitor(accessCode(headerAccessCode,
                queryAccessCode), agentId);
        return emitter.map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/demo/agents/{agentId}/prompt")
    public ResponseEntity<PolicyResponseView> prompt(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable UUID agentId,
            @RequestParam(required = false) String profile) {
        OutputProfile outputProfile = OutputProfile.fromNullable(profile);
        if (outputProfile == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return this.demoService.prompt(accessCode(headerAccessCode, queryAccessCode), agentId, outputProfile)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping(path = "/demo/agents/{agentId}/realtime/call", consumes = { "application/sdp",
            MediaType.TEXT_PLAIN_VALUE })
    public ResponseEntity<RealtimeCallView> realtimeCall(
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable UUID agentId,
            @RequestBody(required = false) String offerSdp,
            @RequestParam(required = false) String voice,
            @RequestParam(required = false) String turnDetection,
            @RequestParam(defaultValue = "true") boolean generateComplement,
            @RequestParam(required = false) String vadThreshold,
            @RequestParam(required = false) String vadPrefixPaddingMs,
            @RequestParam(required = false) String vadSilenceDurationMs,
            @RequestParam(required = false) String vadEagerness,
            @RequestParam(required = false) String vadCreateResponse,
            @RequestParam(required = false) String vadInterruptResponse,
            @RequestParam(required = false) String inputNoiseReduction,
            @RequestParam(required = false) String outputSpeed,
            @RequestParam(required = false) String reasoningEffort,
            @RequestParam(required = false) String maxOutputTokens,
            @RequestParam(required = false) String includeInputTranscriptionLogprobs) {
        if (agentId == null || offerSdp == null || offerSdp.isBlank()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        RealtimeCallSettings settings = new RealtimeCallSettings(voice, turnDetection, generateComplement,
                vadThreshold, vadPrefixPaddingMs, vadSilenceDurationMs, vadEagerness, vadCreateResponse,
                vadInterruptResponse, inputNoiseReduction, outputSpeed, reasoningEffort, maxOutputTokens,
                includeInputTranscriptionLogprobs);
        Optional<RealtimeCallInfo> call = this.realtimeCallService.createScopedCall(
                accessCode(headerAccessCode, queryAccessCode), agentId, offerSdp, settings);
        return call.map(ScopedDemoController::toRealtimeCallView)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(DemoAccessDeniedException.class)
    public ResponseEntity<Void> unauthorized() {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DemoAgentTypeForbiddenException.class)
    public ResponseEntity<Void> forbidden() {
        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> badRequest() {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    private static String accessCode(String headerAccessCode, String queryAccessCode) {
        return queryAccessCode == null || queryAccessCode.isBlank() ? headerAccessCode : queryAccessCode;
    }

    private static boolean isValidEventRequest(EventRequest request) {
        if (request == null || request.getType() == null || request.getType().isBlank()
                || request.getActor() == null || request.getActor().isBlank()
                || request.getKind() == null || request.getKind().isBlank()) {
            return false;
        }
        return request.getPayload() != null && !request.getPayload().isBlank();
    }

    private static RealtimeCallView toRealtimeCallView(RealtimeCallInfo call) {
        return new RealtimeCallView(call.getSdp(), call.getModel(), call.getCallId());
    }
}
