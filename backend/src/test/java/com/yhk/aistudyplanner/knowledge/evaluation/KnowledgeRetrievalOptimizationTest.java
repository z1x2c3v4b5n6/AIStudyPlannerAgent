package com.yhk.aistudyplanner.knowledge.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.repository.ResourceKnowledgeRepository;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class KnowledgeRetrievalOptimizationTest {
    private static final List<Integer> CUT_OFFS = List.of(3, 5, 8);

    @Test
    void comparesRankingsAndImprovesAtThreeInBothDomains() throws IOException {
        var mapper = new ObjectMapper();
        var cases = KnowledgeRetrievalEvaluationDataset.load(mapper);
        var before = KnowledgeRetrievalBaselineTest.baseline(mapper, cases);
        var repository = new ResourceKnowledgeRepository(mapper, new DefaultResourceLoader());
        var after = new KnowledgeRetrievalEvaluator(new KnowledgeRetriever(repository));

        compare("ALL", cases, before, after);
        for (KnowledgeDomain domain : KnowledgeDomain.values()) {
            compare(domain.name(), cases.stream().filter(item -> item.domain() == domain).toList(), before, after);
        }

        var baseline = before.evaluate(cases, 8);
        var optimized = after.evaluate(cases, 8);
        System.out.println(new KnowledgeRetrievalEvaluationReportFormatter().format(CUT_OFFS.stream()
                .map(k -> after.evaluate(cases, k)).toList()));
        System.out.println("Relevant ID rank changes (C-3 -> C-4, - = outside Top-8)");
        for (int i = 0; i < cases.size(); i++) {
            var original = baseline.results().get(i);
            var current = optimized.results().get(i);
            System.out.println(original.caseId() + " | " + original.query() + " | "
                    + original.relevantIds().stream().map(id -> id + ": "
                    + rank(original, id) + " -> " + rank(current, id)).toList()
                    + " | C-3 " + original.retrievedIds() + " | C-4 " + current.retrievedIds());
        }
    }

    private void compare(String scope, List<KnowledgeRetrievalEvaluationCase> cases,
                         KnowledgeRetrievalEvaluator before, KnowledgeRetrievalEvaluator after) {
        for (int k : CUT_OFFS) {
            var baseline = before.evaluate(cases, k).metrics();
            var optimized = after.evaluate(cases, k).metrics();
            System.out.printf(Locale.ROOT, "%s (n=%d) @%d | C-3 %s | C-4 %s%n",
                    scope, cases.size(), k, numbers(baseline), numbers(optimized));
            if (k == 3) {
                assertTrue(optimized.hitAtK() > baseline.hitAtK(), scope + " Hit@3");
                assertTrue(optimized.meanRecallAtK() > baseline.meanRecallAtK(), scope + " Recall@3");
                assertTrue(optimized.mrrAtK() > baseline.mrrAtK(), scope + " MRR@3");
            }
        }
    }

    private String numbers(KnowledgeRetrievalMetrics metrics) {
        return String.format(Locale.ROOT, "%.4f | %.4f | %.4f | %.4f",
                metrics.hitAtK(), metrics.meanPrecisionAtK(), metrics.meanRecallAtK(), metrics.mrrAtK());
    }

    private String rank(KnowledgeRetrievalEvaluationResult result, String id) {
        int index = result.retrievedIds().indexOf(id);
        return index < 0 ? "-" : Integer.toString(index + 1);
    }
}
