package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.definition.application.ActiveAgentDefinition;
import ch.zhaw.prometheus.definition.application.ActiveAgentDefinitionCatalog;
import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.model.access.AccessCodeAllowedAgentType;
import ch.zhaw.prometheus.repositories.AccessCodeAllowedAgentTypeRepository;

class AccessCodeDefinitionAssignmentAuditUnitTest {

    @Test
    void identifiesPreservedAssignmentsThatHaveNoActiveRevision() {
        AccessCodeAllowedAgentTypeRepository assignments = mock(AccessCodeAllowedAgentTypeRepository.class);
        ActiveAgentDefinitionCatalog definitions = mock(ActiveAgentDefinitionCatalog.class);
        AccessCodeAllowedAgentType activeAssignment = assignment("core.talk_to_me");
        AccessCodeAllowedAgentType unknownAssignment = assignment("event.removed_demo");
        ActiveAgentDefinition activeDefinition = activeDefinition("core.talk_to_me");
        when(assignments.findAll()).thenReturn(List.of(unknownAssignment, activeAssignment, unknownAssignment));
        when(definitions.list()).thenReturn(List.of(activeDefinition));

        var audit = new AccessCodeDefinitionAssignmentAudit(assignments, definitions);

        assertEquals(Set.of("event.removed_demo"), audit.unresolvedKeys());
    }

    private static AccessCodeAllowedAgentType assignment(String key) {
        AccessCodeAllowedAgentType assignment = mock(AccessCodeAllowedAgentType.class);
        when(assignment.getAgentTypeKey()).thenReturn(key);
        return assignment;
    }

    private static ActiveAgentDefinition activeDefinition(String key) {
        CompiledAgentDefinition compiled = mock(CompiledAgentDefinition.class);
        when(compiled.key()).thenReturn(key);
        return new ActiveAgentDefinition(1, compiled, List.of());
    }
}
