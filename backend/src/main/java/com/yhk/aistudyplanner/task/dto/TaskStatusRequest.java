package com.yhk.aistudyplanner.task.dto;

import com.yhk.aistudyplanner.task.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull(message = "任务状态不能为空") TaskStatus status) {
}

