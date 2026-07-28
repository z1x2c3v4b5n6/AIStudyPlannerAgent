package com.yhk.aistudyplanner.ai.vo;

import java.time.LocalDateTime;

public record SelectableTaskView(
    Long taskId,
    String taskTitle,
    Long subjectId,
    String subjectName,
    String subjectColor,
    String goalTitle,
    Integer estimatedMinutes,
    LocalDateTime deadline,
    Integer priority,
    String status,
    String matchReason,
    boolean recommended) {}
