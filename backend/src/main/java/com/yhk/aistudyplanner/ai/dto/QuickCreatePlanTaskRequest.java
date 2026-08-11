package com.yhk.aistudyplanner.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record QuickCreatePlanTaskRequest(
    @NotBlank(message = "主题名称不能为空")
        @Size(max = 100, message = "主题名称不能超过100个字符")
        String topicName,
    @NotBlank(message = "任务名称不能为空")
        @Size(max = 200, message = "任务名称不能超过200个字符")
        String taskTitle,
    @NotNull(message = "计划日期不能为空") LocalDate planDate,
    @NotNull(message = "预计时长不能为空")
        @Min(value = 1, message = "预计时长必须为1至720分钟")
        @Max(value = 720, message = "预计时长必须为1至720分钟")
        Integer estimatedMinutes,
    @Size(max = 1000, message = "学习要求不能超过1000个字符") String requirement) {}
