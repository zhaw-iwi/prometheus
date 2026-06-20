package ch.zhaw.prometheus.agentdefs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.agentdefs.basic.FourStatesCircular;
import ch.zhaw.prometheus.agentdefs.basic.FourStatesLinear;
import ch.zhaw.prometheus.agentdefs.basic.SingleStateCoCreation;
import ch.zhaw.prometheus.agentdefs.basic.SingleStateGuessingGame;
import ch.zhaw.prometheus.agentdefs.basic.SingleStateMicroCoaching;
import ch.zhaw.prometheus.agentdefs.gigielderlycare.SingleStateGuessingGameUserGuess;
import ch.zhaw.prometheus.agentdefs.gigielderlycare.SingleStateSmartGoalCoaching;
import ch.zhaw.prometheus.agentdefs.gigielderlycare.SingleStateTherapyAppointmentReminder;
import ch.zhaw.prometheus.agentdefs.gigitdsr.GuessingGameWithGestures;
import ch.zhaw.prometheus.agentdefs.gigitdsr.RockScissorPaper;
import ch.zhaw.prometheus.agentdefs.gigitdsr.SocialContextSensitivity;
import ch.zhaw.prometheus.agentdefs.gigitdsr.TourConversation;

@Component
public class AgentDefinitionRegistry {
    private final Map<String, AgentDefinition> definitions;

    public AgentDefinitionRegistry() {
        this(List.of(
                new SingleStateGuessingGame(),
                new SingleStateMicroCoaching(),
                new SingleStateCoCreation(),
                new FourStatesCircular(),
                new FourStatesLinear(),
                new ch.zhaw.prometheus.agentdefs.multimodal.SingleStateMultimodalIn(),
                new ch.zhaw.prometheus.agentdefs.multimodal.SingleStateMultimodalOut(),
                new ch.zhaw.prometheus.agentdefs.multimodal.SingleStateMultimodalInOut(),
                new GuessingGameWithGestures(),
                new SocialContextSensitivity(),
                new RockScissorPaper(),
                new TourConversation(),
                new SingleStateTherapyAppointmentReminder(),
                new ch.zhaw.prometheus.agentdefs.gigielderlycare.SingleStateGuessingGame(),
                new SingleStateGuessingGameUserGuess(),
                new SingleStateSmartGoalCoaching()));
    }

    AgentDefinitionRegistry(List<AgentDefinition> definitions) {
        LinkedHashMap<String, AgentDefinition> indexed = new LinkedHashMap<>();
        for (AgentDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("agent definition must not be null");
            }
            AgentDefinition previous = indexed.putIfAbsent(definition.key(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate agent definition key: " + definition.key());
            }
        }
        this.definitions = Collections.unmodifiableMap(indexed);
    }

    public List<AgentDefinition> list() {
        return List.copyOf(this.definitions.values());
    }

    public Optional<AgentDefinition> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.definitions.get(key));
    }
}
