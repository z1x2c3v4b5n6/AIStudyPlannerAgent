package com.yhk.aistudyplanner.knowledge.evaluation;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import java.util.List;

public record KnowledgeRetrievalEvaluationCase(
        String id, String query, KnowledgeDomain domain, List<String> relevantIds) {

    public KnowledgeRetrievalEvaluationCase {
        relevantIds = relevantIds == null ? List.of() : List.copyOf(relevantIds);
    }
}
