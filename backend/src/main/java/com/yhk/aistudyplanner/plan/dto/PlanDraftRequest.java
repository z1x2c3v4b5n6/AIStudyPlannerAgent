package com.yhk.aistudyplanner.plan.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;

public record PlanDraftRequest(@NotNull LocalDate planDate, @NotNull LocalTime startTime,
        @NotNull @Min(1) @Max(720) Integer availableMinutes,
        @Size(max=1000) String requirement) {}
