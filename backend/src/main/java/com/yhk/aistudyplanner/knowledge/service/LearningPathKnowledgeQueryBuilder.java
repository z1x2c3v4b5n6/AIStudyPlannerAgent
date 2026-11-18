package com.yhk.aistudyplanner.knowledge.service;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathGenerateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LearningPathKnowledgeQueryBuilder {
    private static final Set<String> JAVA_TERMS = Set.of(
            "java", "jvm", "spring", "hashmap", "concurrenthashmap", "juc", "gc", "synchronized",
            "集合", "并发", "线程池");
    private static final Set<String> AI_TERMS = Set.of(
            "llm", "prompt", "rag", "agent", "embedding", "retrieval",
            "tool calling", "tool use", "context injection", "system prompt", "vector retrieval",
            "提示词", "人工智能", "ai", "ai应用");
    private final KnowledgeQueryNormalizer normalizer = new KnowledgeQueryNormalizer();

    public RetrievalQuery build(LearningPathGenerateRequest request) {
        List<String> parts = new ArrayList<>();
        add(parts, request.requirement());
        add(parts, request.currentLevel());
        add(parts, request.targetLevel());
        add(parts, request.preferences());
        String query = String.join("\n", parts);
        return new RetrievalQuery(query, detectDomain(query));
    }

    KnowledgeDomain detectDomain(String query) {
        String normalized = normalizer.normalize(query).canonical();
        boolean java = JAVA_TERMS.stream()
                .anyMatch(term -> KnowledgeQueryNormalizer.containsPhrase(normalized, term));
        boolean ai = AI_TERMS.stream()
                .anyMatch(term -> KnowledgeQueryNormalizer.containsPhrase(normalized, term));
        if (java == ai) {
            return null;
        }
        return java ? KnowledgeDomain.JAVA_BACKEND : KnowledgeDomain.AI_APPLICATION;
    }

    private void add(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            String normalized = value.trim();
            if (!parts.contains(normalized)) {
                parts.add(normalized);
            }
        }
    }

    public record RetrievalQuery(String text, KnowledgeDomain domain) {}
}
