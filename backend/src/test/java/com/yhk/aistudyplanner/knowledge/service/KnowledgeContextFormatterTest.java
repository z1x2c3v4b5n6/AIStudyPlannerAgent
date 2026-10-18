package com.yhk.aistudyplanner.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;

import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDifficulty;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeContextFormatterTest {
    private final KnowledgeContextFormatter formatter = new KnowledgeContextFormatter();

    @Test
    void formatsRankedResultsWithoutScoreAliasesOrTags() {
        var context = formatter.format(List.of(result("first", "RAG Architecture"), result("second", "Embedding")));

        assertAll(
                () -> assertTrue(context.text().contains("[1]")),
                () -> assertTrue(context.text().contains("id: first")),
                () -> assertTrue(context.text().indexOf("id: first") < context.text().indexOf("id: second")),
                () -> assertTrue(context.text().contains("matchedTerms:")),
                () -> assertFalse(context.text().contains("score:")),
                () -> assertFalse(context.text().contains("hidden-alias")),
                () -> assertFalse(context.text().contains("hidden-tag")),
                () -> assertEquals(List.of("first", "second"), context.unitIds()));
    }

    @Test
    void limitsListsAndPreservesCompleteUnitBoundariesWithinCharacterLimit() {
        List<KnowledgeSearchResult> results = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            results.add(longResult("unit-" + index));
        }

        var context = formatter.format(results);

        assertAll(
                () -> assertTrue(context.characterCount() <= KnowledgeContextFormatter.MAX_CONTEXT_CHARACTERS),
                () -> assertEquals(context.text().length(), context.characterCount()),
                () -> assertTrue(context.unitCount() > 0),
                () -> assertTrue(context.unitCount() < results.size()),
                () -> assertTrue(context.text().endsWith("matched value 5")),
                () -> assertFalse(context.text().contains("objective value 4")),
                () -> assertFalse(context.text().contains("key point value 6")));
    }

    @Test
    void emptyResultsProduceEmptyContext() {
        var context = formatter.format(List.of());

        assertAll(
                () -> assertEquals("", context.text()),
                () -> assertEquals(0, context.unitCount()),
                () -> assertEquals(0, context.characterCount()),
                () -> assertTrue(context.unitIds().isEmpty()));
    }

    private KnowledgeSearchResult result(String id, String topic) {
        return new KnowledgeSearchResult(
                new KnowledgeUnit(id, KnowledgeDomain.AI_APPLICATION, topic, "RAG",
                        KnowledgeDifficulty.INTERMEDIATE, List.of("hidden-alias"), List.of("LLM"),
                        List.of("理解该知识点在检索增强生成中的作用"), List.of("Grounding"), List.of("hidden-tag")),
                10, List.of(topic));
    }

    private KnowledgeSearchResult longResult(String id) {
        String padding = "x".repeat(220);
        return new KnowledgeSearchResult(
                new KnowledgeUnit(id, KnowledgeDomain.AI_APPLICATION, "topic " + padding, "stage " + padding,
                        KnowledgeDifficulty.INTERMEDIATE, List.of("hidden-alias"),
                        List.of("prerequisite value 1 " + padding, "prerequisite value 2 " + padding,
                                "prerequisite value 3 " + padding, "prerequisite value 4 " + padding),
                        List.of("objective value 1 " + padding, "objective value 2 " + padding,
                                "objective value 3 " + padding, "objective value 4 " + padding),
                        List.of("key point value 1 " + padding, "key point value 2 " + padding,
                                "key point value 3 " + padding, "key point value 4 " + padding,
                                "key point value 5 " + padding, "key point value 6 " + padding),
                        List.of("hidden-tag")),
                10, List.of("matched value 1", "matched value 2", "matched value 3",
                        "matched value 4", "matched value 5", "matched value 6"));
    }
}
