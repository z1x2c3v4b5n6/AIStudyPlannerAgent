package com.yhk.aistudyplanner.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathGenerateRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LearningPathKnowledgeQueryBuilderTest {
    private final LearningPathKnowledgeQueryBuilder builder = new LearningPathKnowledgeQueryBuilder();

    @Test
    void queryUsesOnlyKnowledgeBearingFieldsAndSkipsBlankValues() {
        var request = new LearningPathGenerateRequest(
                "复习Java集合", null, "基础一般", "后端面试", 120,
                LocalDate.of(2026, 8, 23), LocalDate.of(2026, 8, 25), "循序渐进");

        var result = builder.build(request);

        assertAll(
                () -> assertTrue(result.text().contains("复习Java集合")),
                () -> assertTrue(result.text().contains("基础一般")),
                () -> assertTrue(result.text().contains("后端面试")),
                () -> assertTrue(result.text().contains("循序渐进")),
                () -> assertFalse(result.text().contains("2026-08-23")),
                () -> assertFalse(result.text().contains("120")),
                () -> assertEquals(KnowledgeDomain.JAVA_BACKEND, result.domain()));
    }

    @Test
    void obviousAiRequestUsesAiApplicationDomain() {
        var request = new LearningPathGenerateRequest(
                "学习RAG和Prompt", null, null, "构建AI应用", null,
                null, null, "理解Agent工具调用");

        assertEquals(KnowledgeDomain.AI_APPLICATION, builder.build(request).domain());
    }

    @Test
    void mixedOrUncertainRequestSearchesAcrossPacks() {
        var mixed = new LearningPathGenerateRequest(
                "用Java开发RAG应用", null, null, null, null, null, null, null);
        var uncertain = new LearningPathGenerateRequest(
                "准备系统复习", null, null, null, null, null, null, null);

        assertAll(
                () -> assertNull(builder.build(mixed).domain()),
                () -> assertNull(builder.build(uncertain).domain()));
    }
}
