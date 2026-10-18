package com.yhk.aistudyplanner.knowledge.evaluation;

public record KnowledgeRetrievalMetrics(
        int k,
        int caseCount,
        double hitAtK,
        double meanPrecisionAtK,
        double meanRecallAtK,
        double mrrAtK) {}
