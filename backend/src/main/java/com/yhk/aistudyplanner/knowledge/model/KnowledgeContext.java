package com.yhk.aistudyplanner.knowledge.model;

import java.util.List;

public record KnowledgeContext(
        String text, int unitCount, int characterCount, List<String> unitIds) {

    public KnowledgeContext {
        text = text == null ? "" : text;
        unitIds = unitIds == null ? List.of() : List.copyOf(unitIds);
    }

    public static KnowledgeContext empty() {
        return new KnowledgeContext("", 0, 0, List.of());
    }
}
