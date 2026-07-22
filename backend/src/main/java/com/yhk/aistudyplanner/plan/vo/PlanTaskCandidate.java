package com.yhk.aistudyplanner.plan.vo;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
public record PlanTaskCandidate(Long taskId, String taskTitle, Long subjectId, String subjectName,
        String subjectColor, Integer priority, TaskStatus status, Integer estimatedMinutes,
        LocalDate plannedDate, LocalDateTime dueAt) {}
