package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeLiveSourceTest {
    @Test
    @EnabledIfEnvironmentVariable(named="KNOWLEDGE_LIVE_CHECK",matches="true")
    void retrievesAndParsesSpringBootOfficialPage() {
        var entry = new KnowledgeHub.Source("manual-live", "Spring Boot",
                "https://docs.spring.io/spring-boot/reference/index.html",
                KnowledgeDomain.JAVA_BACKEND, null, null, null, null);
        long started = System.nanoTime();
        var document = new OfficialDocuments(true).fetch(entry);
        var chunks = EvidenceText.split(document.title(),document.text());
        assertTrue(EvidenceText.normalize(document.title()).contains("spring boot"));
        assertFalse(chunks.isEmpty());
        System.out.printf("C-10 live official collector: chunks=%d, durationMs=%d%n",chunks.size(),
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
    }
}
