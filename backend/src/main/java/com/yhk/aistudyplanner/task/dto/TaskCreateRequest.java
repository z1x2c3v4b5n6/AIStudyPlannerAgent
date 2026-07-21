package com.yhk.aistudyplanner.task.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskCreateRequest(
        @NotNull(message = "所属科目不能为空") Long subjectId,
        Long goalId,
        @NotBlank(message = "任务标题不能为空") @Size(max = 200, message = "任务标题不能超过200个字符") String title,
        @Size(max = 1000, message = "任务描述不能超过1000个字符") String description,
        @NotNull(message = "任务优先级不能为空") @Min(value = 1, message = "任务优先级必须为1至4") @Max(value = 4, message = "任务优先级必须为1至4") Integer priority,
        @NotNull(message = "预计时长不能为空") @Positive(message = "预计时长必须大于0") Integer estimatedMinutes,
        LocalDate plannedDate,
        LocalDateTime dueAt
) {
}

