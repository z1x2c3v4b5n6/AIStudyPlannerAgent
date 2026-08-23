package com.yhk.aistudyplanner.learningpath.service;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

final class LearningPathQualityValidator {
    private static final Set<String> GENERIC_METHODS = Set.of(
            "学习", "练习", "复习", "阅读", "看资料", "查资料", "认真学习", "多做练习");
    private static final Set<String> GENERIC_CRITERIA = Set.of(
            "掌握", "理解", "学会", "熟练掌握", "深入理解", "完成学习", "完成复习");

    private LearningPathQualityValidator() {}

    static void validateContent(
            String objective, List<String> methods, List<String> criteria) {
        if (!meaningfulObjective(objective)
                || !meaningfulSteps(methods, GENERIC_METHODS, 4)
                || !meaningfulSteps(criteria, GENERIC_CRITERIA, 5)) {
            invalid();
        }
    }

    static void validateOrder(List<Node> nodes) {
        Integer previousDay = null;
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (node.suggestedDay() != null) {
                if (previousDay != null && node.suggestedDay() < previousDay) {
                    invalid();
                }
                previousDay = node.suggestedDay();
            }
            String prerequisite = normalize(node.prerequisite());
            if (prerequisite.isEmpty()) {
                continue;
            }
            for (int futureIndex = index; futureIndex < nodes.size(); futureIndex++) {
                String futureTopic = normalize(nodes.get(futureIndex).topic());
                if (futureTopic.length() >= 2 && prerequisite.contains(futureTopic)) {
                    invalid();
                }
            }
        }
    }

    private static boolean meaningfulObjective(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String compact = value.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "");
        return compact.length() >= 8;
    }

    private static boolean meaningfulSteps(
            List<String> values, Set<String> genericValues, int minimumLength) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        Set<String> distinct = new HashSet<>();
        boolean hasSpecificStep = false;
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String compact = normalize(value);
            if (!distinct.add(compact)) {
                continue;
            }
            if (compact.length() >= minimumLength && !genericValues.contains(compact)) {
                hasSpecificStep = true;
            }
        }
        return hasSpecificStep;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static void invalid() {
        throw new BusinessException(ErrorCode.LEARNING_PATH_INVALID);
    }

    record Node(int sequenceNo, String topic, Integer suggestedDay, String prerequisite) {}
}
