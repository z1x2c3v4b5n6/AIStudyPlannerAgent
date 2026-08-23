package com.yhk.aistudyplanner.learningpath.vo;

public record LearningPathTaskView(Long pathId, Long itemId, Long taskId, String taskTitle,
                                   boolean created, String message) {}
