package com.yhk.aistudyplanner.statistics.vo;

import java.time.LocalDate;

public record DailyTrendView(LocalDate date, long totalMinutes, long recordCount) {
}
