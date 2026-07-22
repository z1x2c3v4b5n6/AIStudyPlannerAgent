package com.yhk.aistudyplanner.statistics.vo;

public record SubjectDistributionView(
        Long subjectId,
        String subjectName,
        String subjectColor,
        long totalMinutes,
        long recordCount,
        double percentage
) {
}
