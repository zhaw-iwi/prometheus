package ch.zhaw.prometheus.definition.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Imports the deterministic bundled catalog and prewarms every active revision before readiness. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DeclarativeDefinitionStartup implements ApplicationRunner {
    private final BundledDefinitionImporter importer;
    private final DefinitionLifecycleService lifecycle;

    public DeclarativeDefinitionStartup(BundledDefinitionImporter importer, DefinitionLifecycleService lifecycle) {
        this.importer = importer;
        this.lifecycle = lifecycle;
    }

    @Override
    public void run(ApplicationArguments args) {
        this.importer.importMainCatalog();
        this.lifecycle.prewarmActive();
    }
}
