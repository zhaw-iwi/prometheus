package ch.zhaw.prometheus.application;

import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.definition.application.ActiveAgentDefinitionCatalog;
import ch.zhaw.prometheus.repositories.AccessCodeAllowedAgentTypeRepository;

/** Reports preserved access-code assignments that no active JSON revision can resolve. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class AccessCodeDefinitionAssignmentAudit implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessCodeDefinitionAssignmentAudit.class);

    private final AccessCodeAllowedAgentTypeRepository assignments;
    private final ActiveAgentDefinitionCatalog definitions;

    public AccessCodeDefinitionAssignmentAudit(AccessCodeAllowedAgentTypeRepository assignments,
            ActiveAgentDefinitionCatalog definitions) {
        this.assignments = assignments;
        this.definitions = definitions;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> unresolved = unresolvedKeys();
        if (!unresolved.isEmpty()) {
            LOGGER.warn("Preserved access-code assignments reference inactive or unknown definition keys: {}",
                    unresolved);
        }
    }

    Set<String> unresolvedKeys() {
        Set<String> activeKeys = this.definitions.list().stream()
                .map(definition -> definition.compiled().key()).collect(java.util.stream.Collectors.toSet());
        Set<String> unresolved = new TreeSet<>();
        this.assignments.findAll().stream().map(assignment -> assignment.getAgentTypeKey())
                .filter(key -> !activeKeys.contains(key)).forEach(unresolved::add);
        return unresolved;
    }
}
