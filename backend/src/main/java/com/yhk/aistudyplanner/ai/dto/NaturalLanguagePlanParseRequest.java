package com.yhk.aistudyplanner.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record NaturalLanguagePlanParseRequest(
    @NotBlank @Size(max = 1000) String text,
    LocalDate fallbackPlanDate,
    LocalTime fallbackStartTime,
    @Min(1) @Max(720) Integer fallbackAvailableMinutes) {}
