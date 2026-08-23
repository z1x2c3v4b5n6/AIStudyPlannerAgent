package com.yhk.aistudyplanner.knowledge.dto;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;

public record KnowledgeSearchRequest(String query, KnowledgeDomain domain, int limit) {
    public static final int DEFAULT_LIMIT = 8;
    public static final int MAX_LIMIT = 20;

    public KnowledgeSearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Knowledge search query must not be blank");
        }
        query = query.trim();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Knowledge search limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public KnowledgeSearchRequest(String query, KnowledgeDomain domain) {
        this(query, domain, DEFAULT_LIMIT);
    }
}
