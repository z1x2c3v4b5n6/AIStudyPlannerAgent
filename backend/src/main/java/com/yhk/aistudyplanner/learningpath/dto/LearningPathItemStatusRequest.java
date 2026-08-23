package com.yhk.aistudyplanner.learningpath.dto;

import com.yhk.aistudyplanner.learningpath.entity.LearningPathItemStatus;
import jakarta.validation.constraints.NotNull;

public record LearningPathItemStatusRequest(@NotNull LearningPathItemStatus status) {}
