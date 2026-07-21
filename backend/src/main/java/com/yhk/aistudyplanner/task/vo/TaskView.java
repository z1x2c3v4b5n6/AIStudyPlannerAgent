package com.yhk.aistudyplanner.task.vo;

import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.entity.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskView(Long id, Long subjectId, Long goalId, String title, String description, Integer priority,
                       TaskStatus status, Integer estimatedMinutes, LocalDate plannedDate, LocalDateTime dueAt,
                       LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static TaskView from(StudyTask task) {
        return new TaskView(task.getId(), task.getSubjectId(), task.getGoalId(), task.getTitle(), task.getDescription(),
                task.getPriority(), task.getStatus(), task.getEstimatedMinutes(), task.getPlannedDate(), task.getDueAt(),
                task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt());
    }
}

