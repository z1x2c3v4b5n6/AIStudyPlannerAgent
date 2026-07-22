package com.yhk.aistudyplanner.statistics.service;

import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.statistics.mapper.StatisticsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 21);
    @Mock private StatisticsMapper mapper;
    @Mock private AuthSessionService sessionService;
    private StatisticsService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        service = new StatisticsService(mapper, sessionService, clock);
        lenient().when(sessionService.currentUserId()).thenReturn(1L);
    }

    @Test
    void summaryUsesCurrentUserAndCalculatesAverages() {
        when(mapper.selectSummary(eq(1L), any(), any()))
                .thenReturn(new StatisticsMapper.SummaryAggregate(420, 8, 5));
        var result = service.summary(TODAY.minusDays(6), TODAY);
        assertEquals(420, result.totalMinutes());
        assertEquals(5, result.activeDays());
        assertEquals(60D, result.averageDailyMinutes());
        assertEquals(84D, result.averageMinutesPerActiveDay());
        verify(mapper).selectSummary(1L, TODAY.minusDays(6).atStartOfDay(), TODAY.plusDays(1).atStartOfDay());
    }

    @Test
    void emptySummaryReturnsZero() {
        when(mapper.selectSummary(eq(1L), any(), any()))
                .thenReturn(new StatisticsMapper.SummaryAggregate(0, 0, 0));
        var result = service.summary(null, null);
        assertEquals(0D, result.averageDailyMinutes());
        assertEquals(0D, result.averageMinutesPerActiveDay());
        assertEquals(TODAY.minusDays(6), result.startDate());
    }

    @Test
    void dailyTrendSortsAndFillsMissingDates() {
        when(mapper.selectDailyTrend(eq(1L), any(), any())).thenReturn(List.of(
                new StatisticsMapper.DailyAggregate(TODAY.minusDays(2), 60, 1),
                new StatisticsMapper.DailyAggregate(TODAY, 90, 2)));
        var result = service.dailyTrend(TODAY.minusDays(2), TODAY);
        assertEquals(List.of(TODAY.minusDays(2), TODAY.minusDays(1), TODAY),
                result.stream().map(item -> item.date()).toList());
        assertEquals(0, result.get(1).totalMinutes());
        assertEquals(0, result.get(1).recordCount());
    }

    @Test
    void subjectDistributionKeepsMapperOrderAndCalculatesPercentage() {
        when(mapper.selectSubjectDistribution(eq(1L), any(), any())).thenReturn(List.of(
                new StatisticsMapper.SubjectAggregate(2L, "数学", "#111111", 300, 5),
                new StatisticsMapper.SubjectAggregate(3L, "英语", "#222222", 120, 2)));
        var result = service.subjectDistribution(TODAY.minusDays(6), TODAY);
        assertEquals(List.of(2L, 3L), result.stream().map(item -> item.subjectId()).toList());
        assertEquals(71.43D, result.get(0).percentage());
        assertEquals(28.57D, result.get(1).percentage());
    }

    @Test
    void emptyDistributionReturnsEmptyList() {
        when(mapper.selectSubjectDistribution(eq(1L), any(), any())).thenReturn(List.of());
        assertTrue(service.subjectDistribution(null, null).isEmpty());
    }

    @Test
    void rejectsInvalidAndOversizedRangesBeforeQuery() {
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.summary(TODAY, TODAY.minusDays(1)));
        assertEquals(ErrorCode.INVALID_DATE_RANGE, invalid.getErrorCode());
        BusinessException large = assertThrows(BusinessException.class,
                () -> service.dailyTrend(TODAY.minusDays(366), TODAY));
        assertEquals(ErrorCode.DATE_RANGE_TOO_LARGE, large.getErrorCode());
        verifyNoInteractions(mapper);
    }
}
