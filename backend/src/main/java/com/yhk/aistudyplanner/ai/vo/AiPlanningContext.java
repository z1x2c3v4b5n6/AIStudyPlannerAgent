package com.yhk.aistudyplanner.ai.vo;

import com.yhk.aistudyplanner.task.entity.TaskStatus;
import java.time.*;
import java.util.List;

public record AiPlanningContext(
    List<SubjectContext> subjects,
    List<GoalContext> goals,
    List<TaskContext> tasks,
    StudySummaryContext recentSummary,
    List<SubjectStudyContext> subjectStudy,
    List<RecordContext> recentRecords) {
  public record SubjectContext(Long id, String name, String color) {}

  public record GoalContext(Long id, Long subjectId, String title, LocalDate targetDate) {}

  public record TaskContext(
      Long id,
      Long subjectId,
      String subjectName,
      String subjectColor,
      Long goalId,
      String goalTitle,
      String title,
      String description,
      Integer priority,
      TaskStatus status,
      Integer estimatedMinutes,
      LocalDate plannedDate,
      LocalDateTime dueAt) {}

  public record StudySummaryContext(long totalMinutes, long recordCount, long activeDays) {}

  public record SubjectStudyContext(Long subjectId, String subjectName, long totalMinutes) {}

  public record RecordContext(
      Long subjectId,
      String subjectName,
      Long taskId,
      String taskTitle,
      LocalDateTime startedAt,
      Integer durationMinutes,
      String feedback) {}
}
