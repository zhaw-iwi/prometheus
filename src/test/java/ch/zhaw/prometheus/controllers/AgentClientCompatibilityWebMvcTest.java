package ch.zhaw.prometheus.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.repositories.AgentRepository;

@SuppressWarnings("null")
@WebMvcTest(controllers = { AgentController.class, AgentControllerRealtime.class, AgentMonitorController.class })
class AgentClientCompatibilityWebMvcTest {

        private static final UUID TEST_AGENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AgentRepository repository;
        @MockitoBean
        private AgentMonitorBroadcaster monitorBroadcaster;

        private Agent agent;

        @BeforeEach
        void setUpAgentFixture() {
                this.agent = new Agent(
                                "Example Conversational Agent",
                                "Test fixture agent for chat, realtime, and monitor compatibility checks.",
                                new State("conversation", new ConversationalPolicy(), List.of()));

                when(this.repository.findById(TEST_AGENT_ID)).thenReturn(Optional.of(this.agent));
                when(this.repository.findById(any(UUID.class))).thenAnswer(invocation -> {
                        UUID requested = invocation.getArgument(0);
                        if (TEST_AGENT_ID.equals(requested)) {
                                return Optional.of(this.agent);
                        }
                        return Optional.empty();
                });
                when(this.repository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(this.monitorBroadcaster.subscribe(eq(TEST_AGENT_ID), any(Supplier.class)))
                                .thenReturn(new SseEmitter(0L));
        }

        @Test
        void chatClientFlowStartRespondAndResetUsesBehaviourPlanEvents() throws Exception {
                this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/start"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.active").value(true))
                                .andExpect(jsonPath("$.responseEvent.type").value(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN))
                                .andExpect(jsonPath("$.responseEvent.payload", containsString("\"speech\"")));

                this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/tick"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.active").value(true))
                                .andExpect(jsonPath("$.responseEvent.type").value(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN));

                this.mockMvc.perform(post("/" + TEST_AGENT_ID + "/respond")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "type":"obs.user_utterance",
                                                  "actor":"user",
                                                  "kind":"observation",
                                                  "payload":"Hello there"
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.active").value(true))
                                .andExpect(jsonPath("$.responseEvent.type").value(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN))
                                .andExpect(jsonPath("$.responseEvent.payload", containsString("Hello there")));

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
                                .andExpect(jsonPath("$.eventHistory[0].actor").value("assistant"))
                                .andExpect(jsonPath("$.eventHistory[1].actor").value("user"));

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
                                .andExpect(jsonPath("$[2].type").value("resp.behaviour_plan"))
                                .andExpect(jsonPath("$[2].payload", containsString("Great to hear that.")));
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

        this.mockMvc.perform(get("/" + TEST_AGENT_ID + "/eventhistory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN));
        }

        private static class ConversationalPolicy extends Policy {
                @Override
                public BehaviourPlan onStart(State state, EventHistory events) {
                        return BehaviourPlan.speechOnly("Hello, I am ready when you are.");
                }

                @Override
                public BehaviourPlan onRespond(State state, EventHistory events) {
                        String lastUserContent = findLastUserObservation(events);
                        if (lastUserContent == null || lastUserContent.isBlank()) {
                                return BehaviourPlan.speechOnly("Thanks, please tell me more.");
                        }
                        return BehaviourPlan.speechOnly("Thanks, I heard: " + lastUserContent);
                }

                @Override
                public String summarise(State state, EventHistory events) {
                        return "";
                }

                @Override
                public String describe() {
                        return "test-conversational-policy";
                }
        }

        private static String findLastUserObservation(EventHistory events) {
                List<Event> eventList = events.toList();
                for (int i = eventList.size() - 1; i >= 0; i--) {
                        Event event = eventList.get(i);
                                if (Event.ACTOR_USER.equals(event.getActor())
                                                && Event.KIND_OBSERVATION.equals(event.getKind())) {
                                return event.getPayload();
                        }
                }
                return null;
        }
}
