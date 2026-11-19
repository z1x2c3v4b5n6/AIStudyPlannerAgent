package com.yhk.aistudyplanner.knowledge.dto;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import java.util.List;

public record KnowledgeSearchResult(KnowledgeUnit unit, int score, List<String> matchedTerms, List<MatchReason> matchReasons) {
    public KnowledgeSearchResult(KnowledgeUnit unit, int score, List<String> matchedTerms) {
        this(unit, score, matchedTerms, List.of());
    }

    public KnowledgeSearchResult {
        if (unit == null || score <= 0) {
            throw new IllegalArgumentException("Knowledge search result requires a unit and positive score");
        }
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
        matchReasons = matchReasons == null ? List.of() : List.copyOf(matchReasons);
    }

    public record MatchReason(String field, MatchType type, String term, String evidence, int points) {}

    public enum MatchType {
        EXACT, SYNONYM, PARTIAL
    }
}
