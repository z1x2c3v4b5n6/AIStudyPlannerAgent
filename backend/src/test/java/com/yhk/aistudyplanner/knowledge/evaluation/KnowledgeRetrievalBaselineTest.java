package com.yhk.aistudyplanner.knowledge.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.repository.ResourceKnowledgeRepository;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class KnowledgeRetrievalBaselineTest {
    @Test
    void evaluatesDeterministicRetrieverAtThreeFiveAndEight() {
        var mapper = new ObjectMapper();
        var cases = KnowledgeRetrievalEvaluationDataset.load(mapper);
        var repository = new ResourceKnowledgeRepository(mapper, new DefaultResourceLoader());
        var evaluator = new KnowledgeRetrievalEvaluator(new KnowledgeRetriever(repository));

        List<KnowledgeRetrievalEvaluation> evaluations = List.of(
                evaluator.evaluate(cases, 3),
                evaluator.evaluate(cases, 5),
                evaluator.evaluate(cases, 8));

        KnowledgeRetrievalMetrics atEight = evaluations.get(2).metrics();
        assertAll(
                () -> assertEquals(30, atEight.caseCount()),
                () -> assertTrue(atEight.hitAtK() > 0.0),
                () -> assertTrue(atEight.meanRecallAtK() > 0.0),
                () -> assertTrue(atEight.mrrAtK() > 0.0),
                () -> assertFalse(evaluations.get(2).weakCases().isEmpty()),
                () -> assertTrue(evaluations.get(2).weakCases().stream()
                        .allMatch(result -> !result.missingRelevantIds().isEmpty())));

        String report = new KnowledgeRetrievalEvaluationReportFormatter().format(evaluations);
        assertAll(
                () -> assertTrue(report.contains("Cases: 30")),
                () -> assertTrue(report.contains("Hit@3")),
                () -> assertTrue(report.contains("Recall@5")),
                () -> assertTrue(report.contains("MRR@8")),
                () -> assertTrue(report.contains("Weak Cases")),
                () -> assertTrue(report.contains("missing:")));
        System.out.println(report);
    }
}
