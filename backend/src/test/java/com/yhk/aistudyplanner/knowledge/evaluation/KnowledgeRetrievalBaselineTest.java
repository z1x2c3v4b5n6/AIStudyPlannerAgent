package com.yhk.aistudyplanner.knowledge.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class KnowledgeRetrievalBaselineTest {
    @Test
    void reproducesC3BaselineAtThreeFiveAndEight() throws IOException {
        var mapper = new ObjectMapper();
        var cases = KnowledgeRetrievalEvaluationDataset.load(mapper);
        var evaluator = baseline(mapper, cases);

        List<KnowledgeRetrievalEvaluation> evaluations = List.of(
                evaluator.evaluate(cases, 3),
                evaluator.evaluate(cases, 5),
                evaluator.evaluate(cases, 8));

        double[] precision = {0.3333, 0.2861, 0.2711};
        for (int index = 0; index < evaluations.size(); index++) {
            var metrics = evaluations.get(index).metrics();
            assertEquals(0.5667, metrics.hitAtK(), 0.00005);
            assertEquals(precision[index], metrics.meanPrecisionAtK(), 0.00005);
            assertEquals(0.4861, metrics.meanRecallAtK(), 0.00005);
            assertEquals(0.5111, metrics.mrrAtK(), 0.00005);
        }

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

    static KnowledgeRetrievalEvaluator baseline(ObjectMapper mapper,
                                                 List<KnowledgeRetrievalEvaluationCase> cases) throws IOException {
        // Ordered Top-8 results from the unchanged C-3 retriever at db9a9a4.
        Map<String, List<String>> rankings;
        try (var input = new ClassPathResource("evaluation/knowledge-retrieval-c3-rankings.json").getInputStream()) {
            rankings = mapper.readValue(input, new TypeReference<>() {});
        }
        Set<String> caseIds = cases.stream().map(KnowledgeRetrievalEvaluationCase::id).collect(Collectors.toSet());
        assertEquals(caseIds, rankings.keySet());
        return new KnowledgeRetrievalEvaluator((testCase, k) -> {
            List<String> ids = rankings.get(testCase.id());
            return ids.subList(0, Math.min(k, ids.size()));
        });
    }
}
