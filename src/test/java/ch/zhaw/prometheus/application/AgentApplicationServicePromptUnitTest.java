package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.controllers.AgentMetaType;
import ch.zhaw.prometheus.controllers.dto.SingleStateAgentCreateDTO;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.PromptContextAugmenter;
import ch.zhaw.prometheus.model.policy.PromptEventContentAdapter;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class AgentApplicationServicePromptUnitTest {

    @Test
    void promptUsesInjectedAssemblerForPolicyBundleComposition() {
        UUID agentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        State state = new State("s", new PromptPolicy("system-policy", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                List.of());
        Agent agent = new Agent("a", "d", state);

        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);

        PromptEventContentAdapter passthroughAdapter = new PromptEventContentAdapter() {
            @Override
            public boolean supports(ch.zhaw.prometheus.model.event.Event event) {
                return true;
            }

            @Override
            public String toPromptContent(ch.zhaw.prometheus.model.event.Event event) {
                return event == null || event.getPayload() == null ? "" : event.getPayload();
            }
        };
        PromptContextAugmenter markerAugmenter = events -> List.of(PromptMessage.system("ASSEMBLER_MARKER"));
        PromptMessageAssembler assembler = new PromptMessageAssembler(List.of(passthroughAdapter),
                List.of(markerAugmenter));

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster, behaviourBroadcaster, assembler,
                languageModelGateway);
        when(repository.findById(agentId)).thenReturn(Optional.of(agent));

        var result = service.prompt(agentId).orElseThrow();

        assertTrue(result.getPromptMessages().stream()
                .anyMatch(message -> "ASSEMBLER_MARKER".equals(message.getContent())));
    }

    @Test
    void agentInfoIncludesDeclaredInteractionProfile() {
        UUID agentId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        State state = new State("s", new PromptPolicy("system-policy", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                List.of());
        Agent agent = new Agent("a", "d", state);
        agent.setInteractionProfile(AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE),
                List.of(AgentInteractionProfile.MODALITY_SPEECH),
                List.of("demo.social_context")));
        agent.setLanguageCode("DE");

        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster,
                behaviourBroadcaster, assembler, languageModelGateway);
        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(repository.findAll()).thenReturn(List.of(agent));

        var info = service.getAgentInfo(agentId).orElseThrow();
        var listed = service.listAgents().get(0);

        assertTrue(info.getInteractionProfile().supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(info.getInteractionProfile().supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
        assertTrue(info.getInteractionProfile().supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
        assertTrue(listed.getInteractionProfile().getProfileTags()
                .contains("demo.social_context"));
        assertEquals("de", info.getLanguageCode());
        assertEquals("de", listed.getLanguageCode());
    }

    @Test
    void createdSingleStateAgentDefaultsLanguageToEnglish() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        when(languageModelGateway.complete(any())).thenReturn("Hello, I am ready.");
        when(repository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster,
                behaviourBroadcaster, assembler, languageModelGateway);

        var info = service.createSingleStateAgent(singleStateCreateDto()).orElseThrow();

        assertEquals(AgentDefinition.LANGUAGE_ENGLISH, info.getLanguageCode());
    }

    @Test
    void createdSingleStateAgentUsesExplicitLanguageCode() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        when(languageModelGateway.complete(any())).thenReturn("Hallo, ich bin bereit.");
        when(repository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster,
                behaviourBroadcaster, assembler, languageModelGateway);
        SingleStateAgentCreateDTO data = singleStateCreateDto();
        data.setLanguageCode("DE");

        var info = service.createSingleStateAgent(data).orElseThrow();

        assertEquals(AgentDefinition.LANGUAGE_GERMAN, info.getLanguageCode());
    }

    private static SingleStateAgentCreateDTO singleStateCreateDto() {
        SingleStateAgentCreateDTO data = new SingleStateAgentCreateDTO();
        data.setType(AgentMetaType.singleState.getValue());
        data.setAgentName("Digital Companion");
        data.setAgentDescription("Daily check-in conversation.");
        data.setStateName("Check-In Interaction");
        data.setStatePrompt("As a digital therapy coach, conduct daily check-ins.");
        data.setStateStarterPrompt("Compose a short first message.");
        data.setTriggerToFinalPrompt("Return false.");
        data.setGuardToFinalPrompt("Return true.");
        data.setActionToFinalPrompt("Summarize the conversation.");
        return data;
    }
}
