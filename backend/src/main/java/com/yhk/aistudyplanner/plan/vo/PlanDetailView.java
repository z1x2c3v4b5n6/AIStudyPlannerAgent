package com.yhk.aistudyplanner.plan.vo;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import java.time.LocalDate; import java.time.LocalDateTime; import java.util.List;
public record PlanDetailView(Long id, String sourceDraftId, LocalDate planDate, Integer availableMinutes,
        Integer plannedMinutes, String requirement, String summary, PlanStatus status,
        LocalDateTime createdAt, LocalDateTime updatedAt, List<PlanItemView> items) {}
