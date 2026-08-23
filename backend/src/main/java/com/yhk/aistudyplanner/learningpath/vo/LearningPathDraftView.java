package com.yhk.aistudyplanner.learningpath.vo;

import java.time.LocalDate;
import java.util.List;

public record LearningPathDraftView(String title, String summary, Long subjectId,
        String originalRequirement, String currentLevel, String targetLevel,
        Integer dailyMinutes, LocalDate startDate, LocalDate targetDate, String preferences,
        int totalMinutes, List<StageView> stages) {
    public record StageView(int stageNo, String title, String description, List<ItemView> items) {}
    public record ItemView(int sequenceNo, String topic, String learningObjective,
            List<String> learningMethod, List<String> completionCriteria,
            int estimatedMinutes, Integer suggestedDay, String prerequisite, String reason) {}
}
