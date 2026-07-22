package com.yhk.aistudyplanner.plan.dto;
import com.yhk.aistudyplanner.plan.entity.PlanItemStatus;
import jakarta.validation.constraints.NotNull;
public record PlanItemStatusRequest(@NotNull PlanItemStatus status) {}
