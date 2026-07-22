package com.yhk.aistudyplanner.plan.vo;
import com.yhk.aistudyplanner.plan.entity.PlanItemStatus;
import java.time.LocalDateTime;
public record PlanItemView(Long id, Integer sequenceNo, Long taskId, String taskTitle, Long subjectId,
        String subjectName, String subjectColor, LocalDateTime startAt, LocalDateTime endAt,
        Integer plannedMinutes, String reason, PlanItemStatus status) {}
