package com.yhk.aistudyplanner.plan.vo;
import java.time.LocalDateTime;
public record PlanDraftItemView(Integer sequenceNo, Long taskId, String taskTitle, Long subjectId,
        String subjectName, String subjectColor, LocalDateTime startAt, LocalDateTime endAt,
        Integer plannedMinutes, String reason) {}
