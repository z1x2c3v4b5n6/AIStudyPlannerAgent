package com.yhk.aistudyplanner.knowledge.evaluation;

import java.util.List;

public record KnowledgeRetrievalEvaluation(
        KnowledgeRetrievalMetrics metrics,
        List<KnowledgeRetrievalEvaluationResult> results,
        List<KnowledgeRetrievalEvaluationResult> weakCases) {

    public KnowledgeRetrievalEvaluation {
        results = List.copyOf(results);
        weakCases = List.copyOf(weakCases);
    }
}
