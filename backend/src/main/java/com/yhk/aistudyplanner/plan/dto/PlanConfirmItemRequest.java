package com.yhk.aistudyplanner.plan.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record PlanConfirmItemRequest(@NotNull @Min(1) Integer sequenceNo, @NotNull Long taskId,
        @NotNull LocalDateTime startAt, @NotNull LocalDateTime endAt,
        @NotNull @Min(1) Integer plannedMinutes, @NotBlank @Size(max=500) String reason) {}
