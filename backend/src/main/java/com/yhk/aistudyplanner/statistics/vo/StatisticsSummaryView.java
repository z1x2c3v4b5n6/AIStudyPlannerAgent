package com.yhk.aistudyplanner.statistics.vo;

import java.time.LocalDate;

public record StatisticsSummaryView(
        LocalDate startDate,
        LocalDate endDate,
        long totalMinutes,
        long recordCount,
        long activeDays,
        double averageDailyMinutes,
        double averageMinutesPerActiveDay
) {
}
