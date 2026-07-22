package com.yhk.aistudyplanner.record.vo;

import java.time.LocalDateTime;

public record StudyRecordView(
        Long id,
        Long subjectId,
        String subjectName,
        String subjectColor,
        Long taskId,
        String taskTitle,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Integer durationMinutes,
        String feedback,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
