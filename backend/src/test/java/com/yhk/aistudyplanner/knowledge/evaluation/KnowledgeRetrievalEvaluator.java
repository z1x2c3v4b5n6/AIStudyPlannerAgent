package com.yhk.aistudyplanner.knowledge.evaluation;

import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KnowledgeRetrievalEvaluator {
    private final KnowledgeRetriever retriever;

    public KnowledgeRetrievalEvaluator(KnowledgeRetriever retriever) {
        this.retriever = retriever;
    }

    public KnowledgeRetrievalEvaluation evaluate(
            List<KnowledgeRetrievalEvaluationCase> cases, int k) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("Evaluation cases must not be empty");
        }
        if (k < 1 || k > KnowledgeSearchRequest.MAX_LIMIT) {
            throw new IllegalArgumentException("Evaluation K is outside the retriever limit");
        }

        List<KnowledgeRetrievalEvaluationResult> results = cases.stream()
                .map(evaluationCase -> evaluateCase(evaluationCase, k))
                .toList();
        double caseCount = results.size();
        KnowledgeRetrievalMetrics metrics = new KnowledgeRetrievalMetrics(
                k,
                results.size(),
                results.stream().filter(KnowledgeRetrievalEvaluationResult::hit).count() / caseCount,
                results.stream().mapToDouble(KnowledgeRetrievalEvaluationResult::precision).sum() / caseCount,
                results.stream().mapToDouble(KnowledgeRetrievalEvaluationResult::recall).sum() / caseCount,
                results.stream().mapToDouble(KnowledgeRetrievalEvaluationResult::reciprocalRank).sum() / caseCount);
        List<KnowledgeRetrievalEvaluationResult> weakCases = results.stream()
                .filter(result -> result.recall() < 1.0)
                .toList();
        return new KnowledgeRetrievalEvaluation(metrics, results, weakCases);
    }

    private KnowledgeRetrievalEvaluationResult evaluateCase(
            KnowledgeRetrievalEvaluationCase evaluationCase, int k) {
        List<String> retrievedIds = retriever.search(new KnowledgeSearchRequest(
                        evaluationCase.query(), evaluationCase.domain(), k)).stream()
                .map(result -> result.unit().id())
                .toList();
        Set<String> relevantIds = new HashSet<>(evaluationCase.relevantIds());
        long relevantRetrieved = retrievedIds.stream().filter(relevantIds::contains).count();
        double precision = retrievedIds.isEmpty()
                ? 0.0
                : (double) relevantRetrieved / retrievedIds.size();
        double recall = (double) relevantRetrieved / relevantIds.size();
        double reciprocalRank = 0.0;
        for (int index = 0; index < retrievedIds.size(); index++) {
            if (relevantIds.contains(retrievedIds.get(index))) {
                reciprocalRank = 1.0 / (index + 1);
                break;
            }
        }
        List<String> missing = new ArrayList<>(evaluationCase.relevantIds());
        missing.removeAll(retrievedIds);
        return new KnowledgeRetrievalEvaluationResult(
                evaluationCase.id(), evaluationCase.query(), retrievedIds,
                evaluationCase.relevantIds(), missing, relevantRetrieved > 0,
                reciprocalRank, precision, recall);
    }
}
