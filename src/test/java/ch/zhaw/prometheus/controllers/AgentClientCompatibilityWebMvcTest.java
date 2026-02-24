package ch.zhaw.prometheus.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.application.BehaviourGenerationOutcome;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PolicyResult;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.OutputProfile;

@SuppressWarnings("null")
@WebMvcTest(controllers = { AgentController.class, AgentControllerRealtime.class, AgentMonitorController.class,
        AgentBehaviourController.class })
class AgentClientCompatibilityWebMvcTest {

    private static final UUID TEST_AGENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentApplicationService agentService;

    @BeforeEach
    void setUpFixture() {
        Event startEvent = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"Hello, I am ready when you are.\"}");
        Event generatedEvent = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"Thanks, please tell me more.\"}");
        Event appendedAssistant = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"Great to hear that.\"}");

        when(this.agentService.start(TEST_AGENT_ID)).thenReturn(Optional.of(new ResponseView(startEvent, true)));
        when(this.agentService.reset(TEST_AGENT_ID)).thenReturn(Optional.of(new ResponseView(startEvent, true)));
        when(this.agentService.acknowledge(eq(TEST_AGENT_ID), any())).thenReturn(true);
        when(this.agentService.generate(eq(TEST_AGENT_ID), isNull(), eq(OutputProfile.FULL_PLAN)))
                .thenReturn(BehaviourGenerationOutcome.GENERATED);
        when(this.agentService.generate(eq(TEST_AGENT_ID), any(), any()))
                .thenReturn(BehaviourGenerationOutcome.GENERATED);

        when(this.agentService.getAgentEventHistory(TEST_AGENT_ID)).thenReturn(Optional.of(List.of(
                startEvent,
                Event.systemTick(),
                generatedEvent,
                Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "I feel good today"),
                appendedAssistant)));

        State promptState = new State("conversation",
                new PromptPolicy("system prompt", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT), List.of());
        PolicyResult policyResult = new PolicyResult(promptState, List.of(
                PromptMessage.system("system prompt"),
                PromptMessage.assistant("Hello, I am ready when you are."),
                PromptMessage.user("I feel good today")));
        when(this.agentService.prompt(TEST_AGENT_ID, OutputProfile.FULL_PLAN))
                .thenReturn(Optional.of(new PolicyResponseView(policyResult, true)));
        when(this.agentService.prompt(TEST_AGENT_ID, OutputProfile.REALTIME_SPEECH))
                .thenReturn(Optional.of(new PolicyResponseView(policyResult, true)));
        when(this.agentService.getAgentInfo(TEST_AGENT_ID))
                .thenReturn(Optional.of(new AgentInfoView(TEST_AGENT_ID, "Example Conversational Agent",
                        "Test fixture agent for chat, realtime, and monitor compatibility checks.", true)));
        when(this.agentService.getAgentState(TEST_AGENT_ID))
                .thenReturn(Optional.of(new AgentStateInfoView("conversation", null, List.of())));
        when(this.agentService.getAgentStorage(TEST_AGENT_ID)).thenReturn(Optional.of(List.of()));
        when(this.agentService.subscribeMonitor(TEST_AGENT_ID)).thenReturn(Optional.of(new SseEmitter(0L)));
        when(this.agentService.subscribeBehaviour(TEST_AGENT_ID)).thenReturn(Optional.of(new SseEmitter(0L)));
    }

    @Test
    void chatClientFlowStartGenerateAndResetUsesBehaviourPlanEvents() throws Exception {
        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.responseEvent.type").value(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN))
                .andExpect(jsonPath("$.responseEvent.payload", containsString("\"speech\"")));

        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/acknowledge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "type":"obs.user_utterance",
                          "actor":"user",
                          "kind":"observation",
                          "payload":"Hello there"
                        }
                        """))
                .andExpect(status().isOk());

        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/behaviour/generate"))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/eventhistory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("assistant"))
                .andExpect(jsonPath("$[1].type").value(Event.TYPE_SYSTEM_TICK))
                .andExpect(jsonPath("$[2].actor").value("assistant"))
                .andExpect(jsonPath("$[3].actor").value("user"))
                .andExpect(jsonPath("$[4].actor").value("assistant"));

        this.mockMvc.perform(delete("/" + TEST_AGENT_ID + "/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.responseEvent.type").value(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN));
    }

    @Test
    void realtimeClientFlowAcknowledgePromptAndAssistantAppend() throws Exception {
        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/start"))
                .andExpect(status().isOk());

        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/acknowledge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "type":"obs.user_utterance",
                          "actor":"user",
                          "kind":"observation",
                          "payload":"I feel good today"
                        }
                        """))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/prompt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.stateName").value("conversation"))
                .andExpect(jsonPath("$.promptMessages[0].role").value("system"))
                .andExpect(jsonPath("$.promptMessages[1].role").value("assistant"))
                .andExpect(jsonPath("$.promptMessages[2].role").value("user"));

        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/acknowledge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "type":"resp.behaviour_plan",
                          "actor":"assistant",
                          "kind":"response",
                          "payload":"{\\"speech\\":\\"Great to hear that.\\"}"
                        }
                        """))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/eventhistory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[4].type").value("resp.behaviour_plan"))
                .andExpect(jsonPath("$[4].payload", containsString("Great to hear that.")));
    }

    @Test
    void monitorClientFlowInfoStateStorageAndEventHistoryEndpoints() throws Exception {
        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/start"))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Example Conversational Agent"))
                .andExpect(jsonPath("$.active").value(true));

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("conversation"));

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/monitor/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/behaviour/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/behaviour/generate"))
                .andExpect(status().isOk());

        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/behaviour/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "omitModalities":["speech"]
                        }
                        """))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/eventhistory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN));
    }

    @Test
    void behaviourGenerateReturnsConflictWhenNoBehaviourProduced() throws Exception {
        when(this.agentService.generate(eq(TEST_AGENT_ID), isNull(), eq(OutputProfile.FULL_PLAN)))
                .thenReturn(BehaviourGenerationOutcome.NO_BEHAVIOUR_GENERATED);

        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/behaviour/generate"))
                .andExpect(status().isConflict());
    }

    @Test
    void behaviourGenerateReturnsNotFoundWhenAgentMissing() throws Exception {
        when(this.agentService.generate(eq(TEST_AGENT_ID), isNull(), eq(OutputProfile.FULL_PLAN)))
                .thenReturn(BehaviourGenerationOutcome.AGENT_NOT_FOUND);

        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/behaviour/generate"))
                .andExpect(status().isNotFound());
    }

    @Test
    void promptReturnsBadRequestForUnknownProfile() throws Exception {
        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/prompt?profile=unknown_profile"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void behaviourGenerateReturnsBadRequestForUnknownOutputProfile() throws Exception {
        this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/behaviour/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "outputProfile":"no_such_profile"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }
}
