package ch.zhaw.prometheus.config;

import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.hibernate.cfg.AvailableSettings;

import ch.zhaw.prometheus.definition.application.DefinitionRevisionSources;
import ch.zhaw.prometheus.definition.compiled.CompiledDefinitionCache;
import ch.zhaw.prometheus.definition.compiled.DefinitionCacheMetrics;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.component.ComponentRegistry;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeEngine;

@Configuration
public class DeclarativeAgentConfiguration {
    @Bean
    FlywayConfigurationCustomizer existingSchemaBaseline() {
        return configuration -> configuration
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"));
    }

    @Bean
    HibernatePropertiesCustomizer validateFinalSchema() {
        return properties -> properties.put(AvailableSettings.HBM2DDL_AUTO, "validate");
    }

    @Bean
    AgentDefinitionJson agentDefinitionJson() {
        return new AgentDefinitionJson();
    }

    @Bean
    ComponentRegistry declarativeComponentRegistry() {
        return BuiltInComponentCatalog.createRegistry();
    }

    @Bean
    DefinitionCompiler definitionCompiler(ComponentRegistry registry, AgentDefinitionJson definitionJson) {
        return new DefinitionCompiler(registry, definitionJson);
    }

    @Bean
    DefinitionCacheMetrics definitionCacheMetrics() {
        return new DefinitionCacheMetrics();
    }

    @Bean
    DefinitionRevisionSources definitionRevisionSources(DefinitionRepository repository,
            AgentDefinitionJson definitionJson) {
        return new DefinitionRevisionSources(repository, definitionJson);
    }

    @Bean
    CompiledDefinitionCache compiledDefinitionCache(DefinitionCompiler compiler, DefinitionRevisionSources sources,
            DefinitionCacheMetrics metrics) {
        return new CompiledDefinitionCache(compiler, sources, metrics);
    }

    @Bean
    AgentRuntimeEngine declarativeAgentRuntimeEngine() {
        return new AgentRuntimeEngine();
    }
}
