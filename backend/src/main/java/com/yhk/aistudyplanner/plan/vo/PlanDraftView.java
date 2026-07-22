package com.yhk.aistudyplanner.plan.vo;
import java.time.LocalDate; import java.time.LocalTime; import java.util.List;
public record PlanDraftView(String draftId, LocalDate planDate, LocalTime startTime,
        Integer availableMinutes, Integer plannedMinutes, String requirement, String summary,
        List<PlanDraftItemView> items) {}
