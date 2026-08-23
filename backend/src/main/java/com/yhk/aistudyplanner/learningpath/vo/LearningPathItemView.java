package com.yhk.aistudyplanner.learningpath.vo;

import com.yhk.aistudyplanner.learningpath.entity.LearningPathItemStatus;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import java.util.List;

public record LearningPathItemView(Long id, Integer sequenceNo, Integer stageNo, String stageTitle,
        String stageDescription, String topic, String learningObjective,
        List<String> learningMethod, List<String> completionCriteria, Integer estimatedMinutes,
        Integer suggestedDay, String prerequisite, String reason,
        LearningPathItemStatus storedStatus, String effectiveStatus,
        Long taskId, String taskTitle, TaskStatus taskStatus) {}
