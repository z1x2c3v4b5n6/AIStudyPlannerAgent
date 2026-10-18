package com.yhk.aistudyplanner.knowledge.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDifficulty;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalEvaluatorTest {
    private KnowledgeRetriever retriever;
    private KnowledgeRetrievalEvaluator evaluator;

    @BeforeEach
    void setUp() {
        retriever = mock(KnowledgeRetriever.class);
        evaluator = new KnowledgeRetrievalEvaluator(retriever);
    }

    @Test
    void firstRankHitHasReciprocalRankOne() {
        when(retriever.search(any())).thenReturn(results("unit-a", "unit-b"));

        var result = evaluate(List.of("unit-a"), 3);

        assertAll(
                () -> assertTrue(result.hit()),
                () -> assertEquals(1.0, result.reciprocalRank()),
                () -> assertEquals(0.5, result.precision()),
                () -> assertEquals(1.0, result.recall()));
    }

    @Test
    void secondRankHitHasReciprocalRankOneHalf() {
        when(retriever.search(any())).thenReturn(results("unit-a", "unit-b"));

        var result = evaluate(List.of("unit-b"), 3);

        assertAll(
                () -> assertTrue(result.hit()),
                () -> assertEquals(0.5, result.reciprocalRank()),
                () -> assertEquals(0.5, result.precision()),
                () -> assertEquals(1.0, result.recall()));
    }

    @Test
    void completeMissProducesZeroMetrics() {
        when(retriever.search(any())).thenReturn(results("unit-a", "unit-b"));

        var result = evaluate(List.of("unit-c"), 3);

        assertAll(
                () -> assertFalse(result.hit()),
                () -> assertEquals(0.0, result.reciprocalRank()),
                () -> assertEquals(0.0, result.precision()),
                () -> assertEquals(0.0, result.recall()),
                () -> assertEquals(List.of("unit-c"), result.missingRelevantIds()));
    }

    @Test
    void multipleRelevantHitsUseAllRelevantItemsForPrecisionAndRecall() {
        when(retriever.search(any())).thenReturn(results("unit-a", "unit-b", "unit-c"));

        var result = evaluate(List.of("unit-a", "unit-c", "unit-d"), 3);

        assertAll(
                () -> assertEquals(2.0 / 3.0, result.precision(), 0.000001),
                () -> assertEquals(2.0 / 3.0, result.recall(), 0.000001),
                () -> assertEquals(1.0, result.reciprocalRank()));
    }

    @Test
    void precisionUsesActualResultCountWhenFewerThanKAreReturned() {
        when(retriever.search(any())).thenReturn(results("unit-a", "unit-b"));

        var result = evaluate(List.of("unit-a"), 8);

        assertEquals(0.5, result.precision());
    }

    @Test
    void emptyResultsProduceZeroPrecisionRecallAndReciprocalRank() {
        when(retriever.search(any())).thenReturn(List.of());

        var result = evaluate(List.of("unit-a"), 8);

        assertAll(
                () -> assertFalse(result.hit()),
                () -> assertEquals(0.0, result.precision()),
                () -> assertEquals(0.0, result.recall()),
                () -> assertEquals(0.0, result.reciprocalRank()));
    }

    private KnowledgeRetrievalEvaluationResult evaluate(List<String> relevantIds, int k) {
        var evaluationCase = new KnowledgeRetrievalEvaluationCase(
                "case-1", "query", KnowledgeDomain.JAVA_BACKEND, relevantIds);
        return evaluator.evaluate(List.of(evaluationCase), k).results().get(0);
    }

    private List<KnowledgeSearchResult> results(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new KnowledgeSearchResult(unit(id), 1, List.of("match")))
                .toList();
    }

    private KnowledgeUnit unit(String id) {
        return new KnowledgeUnit(
                id, KnowledgeDomain.JAVA_BACKEND, id, "test", KnowledgeDifficulty.BEGINNER,
                List.of(), List.of(), List.of("objective"), List.of("point"), List.of("tag"));
    }
}
