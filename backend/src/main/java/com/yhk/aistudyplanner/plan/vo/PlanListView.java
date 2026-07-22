package com.yhk.aistudyplanner.plan.vo;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import java.time.LocalDate; import java.time.LocalDateTime;
public record PlanListView(Long id, LocalDate planDate, Integer availableMinutes, Integer plannedMinutes,
        String requirement, String summary, PlanStatus status, Long totalItemCount, Long completedItemCount,
        Long skippedItemCount, Long pendingItemCount, Double completionPercentage,
        LocalDateTime createdAt, LocalDateTime updatedAt) {}
