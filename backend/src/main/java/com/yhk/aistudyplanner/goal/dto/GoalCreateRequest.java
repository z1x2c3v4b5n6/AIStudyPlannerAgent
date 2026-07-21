package com.yhk.aistudyplanner.goal.dto;

import com.yhk.aistudyplanner.goal.entity.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GoalCreateRequest(
        @NotNull(message = "所属科目不能为空") Long subjectId,
        @NotBlank(message = "目标标题不能为空") @Size(max = 200, message = "目标标题不能超过200个字符") String title,
        @Size(max = 1000, message = "目标描述不能超过1000个字符") String description,
        @NotNull(message = "目标总时长不能为空") @Positive(message = "目标总时长必须大于0") Integer targetMinutes,
        LocalDate targetDate,
        @NotNull(message = "目标状态不能为空") GoalStatus status
) {
}

