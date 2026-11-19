package com.yhk.aistudyplanner.learningpath.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathModelResponse;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class GenerationRubric {
    private static final Map<String, String> ROOT = Map.of("title", "text", "summary", "nullable", "stages", "array");
    private static final Map<String, String> STAGE = Map.of("stageNo", "int", "title", "text",
            "description", "nullable", "items", "array");
    private static final Map<String, String> ITEM = Map.of("sequenceNo", "int", "topic", "text",
            "learningObjective", "text", "learningMethod", "strings", "completionCriteria", "strings",
            "estimatedMinutes", "int", "suggestedDay", "int", "prerequisite", "nullable", "reason", "nullable");

    static boolean schema(ObjectMapper mapper, String raw) {
        try {
            JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(raw);
            if (!shape(root, ROOT)) return false;
            for (JsonNode stage : root.get("stages")) {
                if (!shape(stage, STAGE)) return false;
                for (JsonNode item : stage.get("items")) {
                    if (!shape(item, ITEM)) return false;
                }
            }
            return true;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return false;
        }
    }

    private static boolean shape(JsonNode node, Map<String, String> fields) {
        return node != null && node.isObject() && node.size() == fields.size()
                && fields.entrySet().stream().allMatch(field -> {
                    JsonNode value = node.path(field.getKey());
                    return switch (field.getValue()) {
                        case "text" -> value.isTextual();
                        case "nullable" -> value.isNull() || value.isTextual();
                        case "array" -> value.isArray() && !value.isEmpty() && value.size() <= 10;
                        case "int" -> value.isIntegralNumber() && value.canConvertToInt();
                        case "strings" -> value.isArray() && value.size() <= 3
                                && java.util.stream.StreamSupport.stream(value.spliterator(), false)
                                        .allMatch(JsonNode::isTextual);
                        default -> throw new IllegalStateException("Unknown schema type");
                    };
                });
    }

    static Quality score(LearningPathModelResponse response, Case testCase) {
        var nodes = response == null || response.stages() == null ? List.<LearningPathModelResponse.Item>of()
                : response.stages().stream().filter(stage -> stage != null && stage.items() != null)
                        .flatMap(stage -> stage.items().stream()).filter(java.util.Objects::nonNull).toList();
        var topics = nodes.stream().map(node -> normalized(node.topic())).toList();
        var first = testCase.requiredTopics().stream()
                .map(terms -> java.util.stream.IntStream.range(0, nodes.size())
                        .filter(i -> matches(terms, topics.get(i))).findFirst().orElse(-1)).toList();
        var missing = java.util.stream.IntStream.range(0, first.size())
                .filter(i -> first.get(i) < 0).mapToObj(i -> testCase.requiredTopics().get(i).get(0)).toList();
        var broken = testCase.prerequisites().stream().filter(edge -> {
            int before = first.get(edge.get(0));
            int after = first.get(edge.get(1));
            return before < 0 || after <= before || !matches(testCase.requiredTopics().get(edge.get(0)),
                    normalized(nodes.get(after).prerequisite()));
        }).toList();
        var text = new StringBuilder();
        if (response != null) {
            append(text, response.title());
            append(text, response.summary());
            if (response.stages() != null) response.stages().stream().filter(java.util.Objects::nonNull).forEach(stage -> {
                append(text, stage.title());
                append(text, stage.description());
            });
        }
        for (var node : nodes) {
            Stream.of(node.topic(), node.learningObjective(), node.prerequisite(), node.reason())
                    .forEach(value -> append(text, value));
            if (node.learningMethod() != null) node.learningMethod().forEach(value -> append(text, value));
            if (node.completionCriteria() != null) node.completionCriteria().forEach(value -> append(text, value));
        }
        var prohibited = testCase.forbidden().stream()
                .filter(term -> text.toString().contains(normalized(term))).toList();
        Set<String> seen = new HashSet<>();
        long duplicate = topics.stream().filter(topic -> !seen.add(topic)).count();
        var unsupported = topics.stream()
                .filter(topic -> testCase.requiredTopics().stream().noneMatch(terms -> matches(terms, topic))).toList();
        return new Quality(missing, broken, prohibited, unsupported, duplicate, nodes.size());
    }

    private static void append(StringBuilder text, String value) {
        text.append(normalized(value)).append('\n');
    }

    private static boolean matches(List<String> aliases, String value) {
        return aliases.stream().map(GenerationRubric::normalized).anyMatch(value::contains);
    }

    private static String normalized(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{Z}\\s]+", "");
    }

    record Case(String id, String scenario, String requirement, String currentLevel, String targetLevel,
                List<String> oracleIds, List<List<String>> requiredTopics, List<List<Integer>> prerequisites,
                List<String> forbidden, Note noise, List<String> manualFacts) {}
    record Note(String topic, String keyPoint) {}
    record Quality(List<String> missingTopics, List<List<Integer>> brokenPrerequisites,
                   List<String> forbiddenMatches, List<String> unrecognizedTopics, long duplicateNodes, int nodes) {}
}
