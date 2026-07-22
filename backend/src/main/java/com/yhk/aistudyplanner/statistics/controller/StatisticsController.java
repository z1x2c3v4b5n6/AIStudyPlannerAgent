package com.yhk.aistudyplanner.statistics.controller;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import com.yhk.aistudyplanner.statistics.service.StatisticsService;
import com.yhk.aistudyplanner.statistics.vo.DailyTrendView;
import com.yhk.aistudyplanner.statistics.vo.StatisticsSummaryView;
import com.yhk.aistudyplanner.statistics.vo.SubjectDistributionView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/summary")
    public ApiResponse<StatisticsSummaryView> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(statisticsService.summary(startDate, endDate));
    }

    @GetMapping("/daily-trend")
    public ApiResponse<List<DailyTrendView>> dailyTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(statisticsService.dailyTrend(startDate, endDate));
    }

    @GetMapping("/subject-distribution")
    public ApiResponse<List<SubjectDistributionView>> subjectDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(statisticsService.subjectDistribution(startDate, endDate));
    }
}
