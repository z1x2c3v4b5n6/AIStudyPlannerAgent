package com.yhk.aistudyplanner.plan.dto;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import jakarta.validation.constraints.NotNull;
public record PlanStatusRequest(@NotNull PlanStatus status) {}
