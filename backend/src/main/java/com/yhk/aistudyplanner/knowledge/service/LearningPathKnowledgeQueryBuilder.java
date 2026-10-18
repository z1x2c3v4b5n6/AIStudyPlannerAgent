package com.yhk.aistudyplanner.knowledge.service;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathGenerateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LearningPathKnowledgeQueryBuilder {
    private static final Set<String> JAVA_TERMS = Set.of(
            "java", "jvm", "spring", "springboot", "hashmap", "juc",
            "集合", "并发", "线程池", "垃圾回收");
    private static final Set<String> AI_TERMS = Set.of(
            "llm", "prompt", "rag", "agent", "embedding", "retrieval",
            "tool calling", "function calling", "大模型", "提示词", "向量检索",
            "智能体", "工具调用", "人工智能", "ai应用");
    private static final Pattern STANDALONE_AI = Pattern.compile("(?<![a-z0-9])ai(?![a-z0-9])");

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
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        boolean java = JAVA_TERMS.stream().anyMatch(normalized::contains);
        boolean ai = AI_TERMS.stream().anyMatch(normalized::contains)
                || STANDALONE_AI.matcher(normalized).find();
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
