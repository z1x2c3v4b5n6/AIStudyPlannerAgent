package com.yhk.aistudyplanner.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CandidateTasksRequest(
    @NotEmpty List<Long> selectedSubjectIds,
    @NotNull LocalDate planDate,
    @Size(max = 1000) String requirement) {}
