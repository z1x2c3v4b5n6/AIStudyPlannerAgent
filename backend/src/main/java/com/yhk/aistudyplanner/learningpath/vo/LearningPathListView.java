package com.yhk.aistudyplanner.learningpath.vo;

import com.yhk.aistudyplanner.learningpath.entity.LearningPathStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LearningPathListView(Long id, Long subjectId, String subjectName, String subjectColor,
        String title, LearningPathStatus status, LocalDate startDate, LocalDate targetDate,
        Long stageCount, Long totalItems, Long completedItems, Long skippedItems,
        Double progress, String currentStage, LocalDateTime createdAt) {}
