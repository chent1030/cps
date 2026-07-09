package com.company.cps.bootstrap;

import com.company.cps.service.KnowledgeVectorSyncService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cps.knowledge.vector", name = "bootstrap-enabled", havingValue = "true")
public class CpsKnowledgeVectorBootstrap implements ApplicationRunner {

    private final KnowledgeVectorSyncService syncService;

    public CpsKnowledgeVectorBootstrap(KnowledgeVectorSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        syncService.bootstrap();
    }
}
