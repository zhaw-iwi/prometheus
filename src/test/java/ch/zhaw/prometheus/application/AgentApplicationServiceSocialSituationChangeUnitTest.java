package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.social.SocialSituationChangeDetector;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class AgentApplicationServiceSocialSituationChangeUnitTest {

    @Test
    void acknowledgeSocialGroupingPersistsComputedSituationChangeEvents() {
        UUID agentId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Agent agent = newAgent();
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(repository.save(agent)).thenReturn(agent);

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster,
                behaviourBroadcaster, new PromptMessageAssembler(), languageModelGateway);

        service.acknowledge(agentId, groupingRequest(0, 0, 0, 0));
        service.acknowledge(agentId, groupingRequest(1, 0, 1, 1));

        List<Event> changes = agent.getEventHistory().selectList(
                ch.zhaw.prometheus.model.event.EventSelector.type(Event.TYPE_SOCIAL_SITUATION_CHANGE));

        assertEquals(2, changes.size());
        assertEquals(SocialSituationChangeDetector.CHANGE_NOW_ALONE,
                payload(changes.get(0)).get("changeType").getAsString());
        assertEquals(SocialSituationChangeDetector.CHANGE_ARRIVAL,
                payload(changes.get(1)).get("changeType").getAsString());
        assertEquals(Event.ACTOR_SYSTEM, changes.get(1).getActor());
        assertEquals(Event.KIND_OBSERVATION, changes.get(1).getKind());
        verify(repository, times(2)).save(agent);
    }

    @Test
    void acknowledgeNonSocialGroupingEventDoesNotCreateSituationChange() {
        UUID agentId = UUID.fromString("22222222-3333-4444-5555-666666666666");
        Agent agent = newAgent();
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(repository.save(agent)).thenReturn(agent);

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster,
                behaviourBroadcaster, new PromptMessageAssembler(), languageModelGateway);

        service.acknowledge(agentId,
                new EventRequest(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, Event.KIND_OBSERVATION, "Hallo"));

        boolean hasSituationChange = agent.getEventHistory().toList().stream()
                .anyMatch(event -> Event.TYPE_SOCIAL_SITUATION_CHANGE.equals(event.getType()));

        assertTrue(!hasSituationChange);
    }

    private static Agent newAgent() {
        State state = new State("conversation",
                new PromptPolicy("Respond in German.", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                List.of());
        return new Agent("agent", "desc", state);
    }

    private static EventRequest groupingRequest(int humanCount, int groupCount, int singletonCount,
            int largestGroupSize) {
        return new EventRequest(Event.TYPE_SOCIAL_GROUPING, Event.ACTOR_USER, Event.KIND_OBSERVATION,
                "{\"humanCount\":" + humanCount
                        + ",\"groupCount\":" + groupCount
                        + ",\"singletonCount\":" + singletonCount
                        + ",\"largestGroupSize\":" + largestGroupSize + "}");
    }

    private static JsonObject payload(Event event) {
        return JsonParser.parseString(event.getPayload()).getAsJsonObject();
    }
}
