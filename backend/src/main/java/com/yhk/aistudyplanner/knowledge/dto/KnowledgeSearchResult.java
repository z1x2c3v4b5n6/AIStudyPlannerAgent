package com.yhk.aistudyplanner.knowledge.dto;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import java.util.List;

public record KnowledgeSearchResult(KnowledgeUnit unit, int score, List<String> matchedTerms) {
    public KnowledgeSearchResult {
        if (unit == null || score <= 0) {
            throw new IllegalArgumentException("Knowledge search result requires a unit and positive score");
        }
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
    }
}
