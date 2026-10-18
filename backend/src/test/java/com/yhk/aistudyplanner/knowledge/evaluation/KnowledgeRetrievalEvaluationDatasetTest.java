package com.yhk.aistudyplanner.knowledge.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.repository.ResourceKnowledgeRepository;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class KnowledgeRetrievalEvaluationDatasetTest {
    @Test
    void datasetHasUniqueValidCasesAndReferencesExistingKnowledgeUnits() {
        var mapper = new ObjectMapper();
        var cases = KnowledgeRetrievalEvaluationDataset.load(mapper);
        var repository = new ResourceKnowledgeRepository(mapper, new DefaultResourceLoader());
        var caseIds = new HashSet<String>();

        assertTrue(cases.size() >= 25);
        cases.forEach(evaluationCase -> {
            assertAll(
                    () -> assertNotNull(evaluationCase.id()),
                    () -> assertFalse(evaluationCase.id().isBlank()),
                    () -> assertTrue(caseIds.add(evaluationCase.id())),
                    () -> assertNotNull(evaluationCase.query()),
                    () -> assertFalse(evaluationCase.query().isBlank()),
                    () -> assertFalse(evaluationCase.relevantIds().isEmpty()),
                    () -> assertEquals(new HashSet<>(evaluationCase.relevantIds()).size(),
                            evaluationCase.relevantIds().size()));
            evaluationCase.relevantIds().forEach(relevantId ->
                    assertTrue(repository.findById(relevantId).isPresent(),
                            () -> "Unknown relevant knowledge id: " + relevantId));
        });

        long javaCases = cases.stream()
                .filter(value -> value.domain() == KnowledgeDomain.JAVA_BACKEND)
                .count();
        long aiCases = cases.stream()
                .filter(value -> value.domain() == KnowledgeDomain.AI_APPLICATION)
                .count();
        assertAll(
                () -> assertEquals(12, javaCases),
                () -> assertEquals(18, aiCases));
    }
}
