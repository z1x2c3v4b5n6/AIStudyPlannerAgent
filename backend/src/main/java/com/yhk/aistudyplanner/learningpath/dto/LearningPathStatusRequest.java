package com.yhk.aistudyplanner.learningpath.dto;

import com.yhk.aistudyplanner.learningpath.entity.LearningPathStatus;
import jakarta.validation.constraints.NotNull;

public record LearningPathStatusRequest(@NotNull LearningPathStatus status) {}
