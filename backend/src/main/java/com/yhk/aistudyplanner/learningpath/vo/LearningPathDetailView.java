package com.yhk.aistudyplanner.learningpath.vo;

import com.yhk.aistudyplanner.learningpath.entity.LearningPathStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record LearningPathDetailView(Long id, Long subjectId, String subjectName, String subjectColor,
        String title, String summary, String originalRequirement, String goal,
        String currentLevel, String targetLevel, String purpose, String preferences,
        LocalDate startDate, LocalDate targetDate, Integer dailyMinutes, LearningPathStatus status,
        int stageCount, int totalItems, int completedItems, int skippedItems, double progress,
        LocalDateTime createdAt, LocalDateTime updatedAt, List<LearningPathItemView> items) {}
