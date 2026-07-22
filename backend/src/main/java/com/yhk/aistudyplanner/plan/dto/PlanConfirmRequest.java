package com.yhk.aistudyplanner.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record PlanConfirmRequest(@NotBlank @Size(max=64) String draftId, @NotNull LocalDate planDate,
        @NotNull @Min(1) @Max(720) Integer availableMinutes, @NotNull @Min(1) Integer plannedMinutes,
        @Size(max=1000) String requirement, @NotBlank @Size(max=1000) String summary,
        @NotEmpty List<@Valid PlanConfirmItemRequest> items) {}
