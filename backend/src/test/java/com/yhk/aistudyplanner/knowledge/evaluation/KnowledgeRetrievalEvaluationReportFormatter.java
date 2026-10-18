package com.yhk.aistudyplanner.knowledge.evaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class KnowledgeRetrievalEvaluationReportFormatter {
    public String format(List<KnowledgeRetrievalEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            throw new IllegalArgumentException("Evaluations must not be empty");
        }
        StringBuilder report = new StringBuilder("RAG Retrieval Evaluation\n\n")
                .append("Cases: ").append(evaluations.get(0).metrics().caseCount()).append("\n\n");
        evaluations.forEach(evaluation -> appendMetrics(report, evaluation.metrics()));
        KnowledgeRetrievalEvaluation largestK = evaluations.stream()
                .max(Comparator.comparingInt(evaluation -> evaluation.metrics().k()))
                .orElseThrow();
        report.append("Weak Cases (K=").append(largestK.metrics().k()).append("):\n");
        largestK.weakCases().stream()
                .sorted(Comparator.comparingDouble(KnowledgeRetrievalEvaluationResult::recall)
                        .thenComparingDouble(KnowledgeRetrievalEvaluationResult::reciprocalRank)
                        .thenComparing(KnowledgeRetrievalEvaluationResult::caseId))
                .limit(5)
                .forEach(result -> report
                        .append("- ").append(result.caseId()).append("\n")
                        .append("  query: ").append(result.query()).append("\n")
                        .append("  expected: ").append(result.relevantIds()).append("\n")
                        .append("  retrieved: ").append(result.retrievedIds()).append("\n")
                        .append("  missing: ").append(result.missingRelevantIds()).append("\n"));
        return report.toString();
    }

    private void appendMetrics(StringBuilder report, KnowledgeRetrievalMetrics metrics) {
        report.append("K=").append(metrics.k()).append("\n")
                .append(String.format(Locale.ROOT, "Hit@%d: %.4f%n", metrics.k(), metrics.hitAtK()))
                .append(String.format(Locale.ROOT, "Precision@%d: %.4f%n", metrics.k(), metrics.meanPrecisionAtK()))
                .append(String.format(Locale.ROOT, "Recall@%d: %.4f%n", metrics.k(), metrics.meanRecallAtK()))
                .append(String.format(Locale.ROOT, "MRR@%d: %.4f%n%n", metrics.k(), metrics.mrrAtK()));
    }
}
