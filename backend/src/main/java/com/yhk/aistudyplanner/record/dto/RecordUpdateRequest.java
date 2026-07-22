package com.yhk.aistudyplanner.record.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record RecordUpdateRequest(
        @NotNull(message = "所属科目不能为空") Long subjectId,
        Long taskId,
        @NotNull(message = "开始时间不能为空") LocalDateTime startedAt,
        @NotNull(message = "结束时间不能为空") LocalDateTime endedAt,
        @Size(max = 1000, message = "学习反馈不能超过1000个字符") String feedback,
        @Null(message = "学习时长由后端自动计算，不能提交") Integer durationMinutes
) {
}
