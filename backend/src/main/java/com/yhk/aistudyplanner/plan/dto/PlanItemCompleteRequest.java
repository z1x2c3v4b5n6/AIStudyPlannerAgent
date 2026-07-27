package com.yhk.aistudyplanner.plan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlanItemCompleteRequest(
        @NotNull(message = "实际学习时长不能为空")
        @Min(value = 1, message = "实际学习时长必须为1至720分钟")
        @Max(value = 720, message = "实际学习时长必须为1至720分钟")
        Integer actualMinutes,
        @Size(max = 1000, message = "学习反馈不能超过1000个字符")
        String feedback,
        @NotNull(message = "请选择是否同时完成原任务")
        Boolean completeTask) {
}
