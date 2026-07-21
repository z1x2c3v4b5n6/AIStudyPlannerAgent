package com.yhk.aistudyplanner.goal.vo;

import com.yhk.aistudyplanner.goal.entity.GoalStatus;
import com.yhk.aistudyplanner.goal.entity.StudyGoal;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GoalView(Long id, Long subjectId, String title, String description, Integer targetMinutes,
                       LocalDate targetDate, GoalStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static GoalView from(StudyGoal goal) {
        return new GoalView(goal.getId(), goal.getSubjectId(), goal.getTitle(), goal.getDescription(),
                goal.getTargetMinutes(), goal.getTargetDate(), goal.getStatus(), goal.getCreatedAt(), goal.getUpdatedAt());
    }
}

