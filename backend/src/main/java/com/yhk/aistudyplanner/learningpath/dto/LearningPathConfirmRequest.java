package com.yhk.aistudyplanner.learningpath.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record LearningPathConfirmRequest(
        Long subjectId,
        @NotBlank @Size(max=200) String title,
        @Size(max=1000) String summary,
        @NotBlank @Size(max=2000) String originalRequirement,
        @Size(max=500) String goal,
        @Size(max=200) String currentLevel,
        @Size(max=200) String targetLevel,
        @Size(max=500) String purpose,
        @Size(max=1000) String preferences,
        @NotNull LocalDate startDate,
        LocalDate targetDate,
        @Min(15) @Max(720) Integer dailyMinutes,
        @NotEmpty List<@Valid Item> items) {
    public record Item(
            @NotNull @Positive Integer sequenceNo,
            @NotNull @Positive Integer stageNo,
            @NotBlank @Size(max=200) String stageTitle,
            @Size(max=1000) String stageDescription,
            @NotBlank @Size(max=200) String topic,
            @NotBlank @Size(max=1000) String learningObjective,
            @NotEmpty @Size(max=10) List<@NotBlank @Size(max=300) String> learningMethod,
            @NotEmpty @Size(max=10) List<@NotBlank @Size(max=300) String> completionCriteria,
            @NotNull @Min(15) @Max(1440) Integer estimatedMinutes,
            @Positive Integer suggestedDay,
            @Size(max=500) String prerequisite,
            @Size(max=500) String reason) {}
}
