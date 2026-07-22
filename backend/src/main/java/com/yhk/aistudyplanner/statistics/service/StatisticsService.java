package com.yhk.aistudyplanner.statistics.service;

import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.statistics.mapper.StatisticsMapper;
import com.yhk.aistudyplanner.statistics.vo.DailyTrendView;
import com.yhk.aistudyplanner.statistics.vo.StatisticsSummaryView;
import com.yhk.aistudyplanner.statistics.vo.SubjectDistributionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StatisticsService {
    private final StatisticsMapper statisticsMapper;
    private final AuthSessionService sessionService;
    private final Clock clock;

    public StatisticsService(StatisticsMapper statisticsMapper, AuthSessionService sessionService, Clock clock) {
        this.statisticsMapper = statisticsMapper;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatisticsSummaryView summary(LocalDate requestedStart, LocalDate requestedEnd) {
        DateRange range = resolveRange(requestedStart, requestedEnd);
        StatisticsMapper.SummaryAggregate aggregate = statisticsMapper.selectSummary(
                sessionService.currentUserId(), range.from(), range.toExclusive());
        long totalMinutes = aggregate == null ? 0 : aggregate.totalMinutes();
        long recordCount = aggregate == null ? 0 : aggregate.recordCount();
        long activeDays = aggregate == null ? 0 : aggregate.activeDays();
        return new StatisticsSummaryView(range.startDate(), range.endDate(), totalMinutes, recordCount, activeDays,
                divide(totalMinutes, range.dayCount()), divide(totalMinutes, activeDays));
    }

    @Transactional(readOnly = true)
    public List<DailyTrendView> dailyTrend(LocalDate requestedStart, LocalDate requestedEnd) {
        DateRange range = resolveRange(requestedStart, requestedEnd);
        List<StatisticsMapper.DailyAggregate> aggregates = statisticsMapper.selectDailyTrend(
                sessionService.currentUserId(), range.from(), range.toExclusive());
        Map<LocalDate, StatisticsMapper.DailyAggregate> byDate = aggregates.stream()
                .collect(Collectors.toMap(StatisticsMapper.DailyAggregate::date, Function.identity()));
        List<DailyTrendView> result = new ArrayList<>((int) range.dayCount());
        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
            StatisticsMapper.DailyAggregate value = byDate.get(date);
            result.add(value == null ? new DailyTrendView(date, 0, 0)
                    : new DailyTrendView(date, value.totalMinutes(), value.recordCount()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SubjectDistributionView> subjectDistribution(LocalDate requestedStart, LocalDate requestedEnd) {
        DateRange range = resolveRange(requestedStart, requestedEnd);
        List<StatisticsMapper.SubjectAggregate> aggregates = statisticsMapper.selectSubjectDistribution(
                sessionService.currentUserId(), range.from(), range.toExclusive());
        long total = aggregates.stream().mapToLong(StatisticsMapper.SubjectAggregate::totalMinutes).sum();
        if (total == 0) return List.of();
        return aggregates.stream().map(item -> new SubjectDistributionView(
                item.subjectId(), item.subjectName(), item.subjectColor(), item.totalMinutes(), item.recordCount(),
                percentage(item.totalMinutes(), total))).toList();
    }

    DateRange resolveRange(LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = requestedEnd == null ? today : requestedEnd;
        LocalDate start = requestedStart == null ? end.minusDays(6) : requestedStart;
        if (start.isAfter(end)) throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        long dayCount = ChronoUnit.DAYS.between(start, end) + 1;
        if (dayCount > 366) throw new BusinessException(ErrorCode.DATE_RANGE_TOO_LARGE);
        return new DateRange(start, end, dayCount);
    }

    private double divide(long value, long divisor) {
        if (divisor == 0) return 0D;
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP).doubleValue();
    }

    private double percentage(long value, long total) {
        return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue();
    }

    record DateRange(LocalDate startDate, LocalDate endDate, long dayCount) {
        java.time.LocalDateTime from() { return startDate.atStartOfDay(); }
        java.time.LocalDateTime toExclusive() { return endDate.plusDays(1).atStartOfDay(); }
    }
}
