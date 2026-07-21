package com.yhk.aistudyplanner.goal.dto;

import com.yhk.aistudyplanner.goal.entity.GoalStatus;
import jakarta.validation.constraints.NotNull;

public record GoalStatusRequest(@NotNull(message = "目标状态不能为空") GoalStatus status) {
}

