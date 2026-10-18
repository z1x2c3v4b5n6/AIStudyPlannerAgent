package com.yhk.aistudyplanner.knowledge.evaluation;

import java.util.List;

public record KnowledgeRetrievalEvaluationResult(
        String caseId,
        String query,
        List<String> retrievedIds,
        List<String> relevantIds,
        List<String> missingRelevantIds,
        boolean hit,
        double reciprocalRank,
        double precision,
        double recall) {

    public KnowledgeRetrievalEvaluationResult {
        retrievedIds = List.copyOf(retrievedIds);
        relevantIds = List.copyOf(relevantIds);
        missingRelevantIds = List.copyOf(missingRelevantIds);
    }
}
