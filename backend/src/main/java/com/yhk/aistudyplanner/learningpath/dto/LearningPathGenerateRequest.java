package com.yhk.aistudyplanner.learningpath.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record LearningPathGenerateRequest(
        @NotBlank @Size(max=2000) String requirement,
        Long subjectId,
        @Size(max=200) String currentLevel,
        @Size(max=200) String targetLevel,
        @Min(15) @Max(720) Integer dailyMinutes,
        LocalDate startDate,
        LocalDate targetDate,
        @Size(max=1000) String preferences) {}
