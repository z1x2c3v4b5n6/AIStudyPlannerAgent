package com.yhk.aistudyplanner.learningpath.dto;

import java.util.List;

public record LearningPathModelResponse(String title, String summary, List<Stage> stages) {
    public record Stage(Integer stageNo, String title, String description, List<Item> items) {}
    public record Item(Integer sequenceNo, String topic, String learningObjective,
                       List<String> learningMethod, List<String> completionCriteria,
                       Integer estimatedMinutes, Integer suggestedDay,
                       String prerequisite, String reason) {}
}
