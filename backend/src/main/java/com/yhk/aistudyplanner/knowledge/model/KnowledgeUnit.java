package com.yhk.aistudyplanner.knowledge.model;

import java.util.List;
import java.util.Objects;

public record KnowledgeUnit(
        String id,
        KnowledgeDomain domain,
        String topic,
        String stage,
        KnowledgeDifficulty difficulty,
        List<String> aliases,
        List<String> prerequisites,
        List<String> learningObjectives,
        List<String> keyPoints,
        List<String> tags) {

    private static final int MAX_LIST_SIZE = 50;
    private static final int MAX_TEXT_LENGTH = 500;

    public KnowledgeUnit {
        id = required(id, "id");
        if (!id.matches("[a-z0-9][a-z0-9-]{1,99}")) {
            throw new IllegalArgumentException("Knowledge unit id must be a stable lowercase slug: " + id);
        }
        domain = Objects.requireNonNull(domain, "Knowledge unit domain is required");
        topic = required(topic, "topic");
        stage = required(stage, "stage");
        difficulty = Objects.requireNonNull(difficulty, "Knowledge unit difficulty is required");
        aliases = safeList(aliases, "aliases", false);
        prerequisites = safeList(prerequisites, "prerequisites", false);
        learningObjectives = safeList(learningObjectives, "learningObjectives", true);
        keyPoints = safeList(keyPoints, "keyPoints", true);
        tags = safeList(tags, "tags", true);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Knowledge unit " + field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Knowledge unit " + field + " is too long");
        }
        return normalized;
    }

    private static List<String> safeList(List<String> values, String field, boolean required) {
        if (values == null) {
            if (required) {
                throw new IllegalArgumentException("Knowledge unit " + field + " is required");
            }
            return List.of();
        }
        if (values.size() > MAX_LIST_SIZE) {
            throw new IllegalArgumentException("Knowledge unit " + field + " contains too many entries");
        }
        List<String> result = values.stream()
                .map(value -> required(value, field))
                .distinct()
                .toList();
        if (required && result.isEmpty()) {
            throw new IllegalArgumentException("Knowledge unit " + field + " must not be empty");
        }
        return result;
    }
}
