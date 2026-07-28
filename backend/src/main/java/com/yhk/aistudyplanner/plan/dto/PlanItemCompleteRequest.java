package com.yhk.aistudyplanner.plan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PlanItemCompleteRequest(
        @NotNull(message = "实际学习开始时间不能为空")
        LocalDateTime actualStartAt,
        @NotNull(message = "实际学习结束时间不能为空")
        LocalDateTime actualEndAt,
        @Size(max = 1000, message = "学习反馈不能超过1000个字符")
        String feedback,
        @NotNull(message = "请选择是否同时完成原任务")
        Boolean completeTask) {
}
