package com.yhk.aistudyplanner.knowledge.service;

import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeContext;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeContextFormatter {
    static final int MAX_CONTEXT_CHARACTERS = 6000;
    private static final int MAX_VALUE_CHARACTERS = 240;

    public KnowledgeContext format(List<KnowledgeSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return KnowledgeContext.empty();
        }

        StringBuilder context = new StringBuilder("Retrieved Knowledge Context\n");
        List<String> unitIds = new ArrayList<>();
        for (KnowledgeSearchResult result : results) {
            String block = formatUnit(unitIds.size() + 1, result);
            if (context.length() + block.length() > MAX_CONTEXT_CHARACTERS) {
                break;
            }
            context.append(block);
            unitIds.add(result.unit().id());
        }
        if (unitIds.isEmpty()) {
            return KnowledgeContext.empty();
        }
        String text = context.toString().trim();
        return new KnowledgeContext(text, unitIds.size(), text.length(), unitIds);
    }

    private String formatUnit(int index, KnowledgeSearchResult result) {
        KnowledgeUnit unit = result.unit();
        StringBuilder block = new StringBuilder()
                .append("\n[").append(index).append("]\n")
                .append("id: ").append(safe(unit.id())).append('\n')
                .append("topic: ").append(safe(unit.topic())).append('\n')
                .append("stage: ").append(safe(unit.stage())).append('\n')
                .append("difficulty: ").append(unit.difficulty()).append('\n');
        appendList(block, "prerequisites", unit.prerequisites(), 3);
        appendList(block, "learningObjectives", unit.learningObjectives(), 3);
        appendList(block, "keyPoints", unit.keyPoints(), 5);
        appendList(block, "matchedTerms", result.matchedTerms(), 5);
        return block.toString();
    }

    private void appendList(StringBuilder target, String label, List<String> values, int limit) {
        target.append(label).append(":\n");
        if (values == null || values.isEmpty()) {
            target.append("- none\n");
            return;
        }
        values.stream().limit(limit).forEach(value -> target.append("- ").append(safe(value)).append('\n'));
    }

    private String safe(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= MAX_VALUE_CHARACTERS
                ? normalized
                : normalized.substring(0, MAX_VALUE_CHARACTERS);
    }
}
