package com.yhk.aistudyplanner.adaptive;

import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.goal.service.GoalService;
import com.yhk.aistudyplanner.learningpath.service.LearningPathService;
import com.yhk.aistudyplanner.record.service.StudyRecordService;
import com.yhk.aistudyplanner.statistics.service.StatisticsService;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningProfileService {
    public record Window(LocalDate from, LocalDate to) {}
    public record TaskCounts(long dueCohort, long dueCohortCompleted, long completedInWindow,
                             long overdueNow, long dueNextThreeDays, long unplannedHighPriority,
                             long openEstimatedNextThreeDays) {
        public Double completionRate() {
            return dueCohort == 0 ? null : (double) dueCohortCompleted / dueCohort;
        }
    }
    public record PlanSample(long eligibleItems, long pairedItems, long missingCompletedRecords,
                             long pairedPlannedMinutes, long pairedActualMinutes) {
        public long deviationMinutes() { return pairedActualMinutes - pairedPlannedMinutes; }
        public Double deviationRatio() {
            return pairedPlannedMinutes == 0 ? null : (double) deviationMinutes() / pairedPlannedMinutes;
        }
    }
    public record Profile(Window window, Instant generatedAt,
                          com.yhk.aistudyplanner.statistics.vo.StatisticsSummaryView activity,
                          java.util.List<com.yhk.aistudyplanner.statistics.vo.DailyTrendView> trend,
                          java.util.List<com.yhk.aistudyplanner.statistics.vo.SubjectDistributionView> subjectTime,
                          java.util.List<com.yhk.aistudyplanner.subject.vo.SubjectView> subjects,
                          com.yhk.aistudyplanner.common.response.PageResponse<com.yhk.aistudyplanner.goal.vo.GoalView> goals,
                          com.yhk.aistudyplanner.common.response.PageResponse<com.yhk.aistudyplanner.learningpath.vo.LearningPathListView> paths,
                          com.yhk.aistudyplanner.common.response.PageResponse<com.yhk.aistudyplanner.record.vo.StudyRecordView> recentRecords,
                          TaskCounts tasks, PlanSample plans, Double cohortCompletionRate, Double pairedDeviationRatio,
                          long priorWindowMinutes, String sampleStatus) {}

    private final AuthSessionService auth;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final StatisticsService statistics;
    private final SubjectService subjects;
    private final GoalService goals;
    private final LearningPathService paths;
    private final StudyRecordService records;

    public LearningProfileService(AuthSessionService auth, JdbcTemplate jdbc, Clock clock, StatisticsService statistics,
                                  SubjectService subjects, GoalService goals, LearningPathService paths, StudyRecordService records) {
        this.auth = auth; this.jdbc = jdbc; this.clock = clock; this.statistics = statistics; this.subjects = subjects;
        this.goals = goals; this.paths = paths; this.records = records;
    }

    @Transactional(readOnly = true)
    public Profile profile(LocalDate from, LocalDate to, Integer days) {
        long owner = auth.currentUserId();
        Window window = window(from, to, days);
        var start = window.from().atStartOfDay();
        var end = window.to().plusDays(1).atStartOfDay();
        var now = LocalDateTime.now(clock);
        var activity = statistics.summary(window.from(), window.to());
        TaskCounts tasks = jdbc.queryForObject("""
                SELECT
                  COUNT(CASE WHEN status<>'CANCELLED' AND due_at>=? AND due_at<? THEN 1 END),
                  COUNT(CASE WHEN status='COMPLETED' AND due_at>=? AND due_at<? AND completed_at<? THEN 1 END),
                  COUNT(CASE WHEN status='COMPLETED' AND completed_at>=? AND completed_at<? THEN 1 END),
                  COUNT(CASE WHEN status IN ('TODO','IN_PROGRESS') AND due_at<? THEN 1 END),
                  COUNT(CASE WHEN status IN ('TODO','IN_PROGRESS') AND due_at>=? AND due_at<? THEN 1 END),
                  COUNT(CASE WHEN status IN ('TODO','IN_PROGRESS') AND priority>=3 AND planned_date IS NULL THEN 1 END),
                  COALESCE(SUM(CASE WHEN status IN ('TODO','IN_PROGRESS') AND
                    (due_at<? OR planned_date BETWEEN ? AND ?) THEN estimated_minutes ELSE 0 END),0)
                FROM study_task WHERE user_id=?
                """, (r,n) -> new TaskCounts(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),
                        r.getLong(5),r.getLong(6),r.getLong(7)),
                start,end,start,end,end,start,end,now,now,now.toLocalDate().plusDays(3).atStartOfDay(),
                now.toLocalDate().plusDays(3).atStartOfDay(),now.toLocalDate(),now.toLocalDate().plusDays(2),owner);
        PlanSample sample = jdbc.queryForObject("""
                SELECT COUNT(*), COUNT(r.id),
                 COUNT(CASE WHEN i.status='COMPLETED' AND r.id IS NULL THEN 1 END),
                 COALESCE(SUM(CASE WHEN r.id IS NOT NULL THEN i.planned_minutes ELSE 0 END),0),
                 COALESCE(SUM(r.duration_minutes),0)
                FROM study_plan_item i JOIN study_plan p ON p.id=i.plan_id AND p.user_id=i.user_id
                LEFT JOIN study_record r ON r.plan_item_id=i.id AND r.user_id=i.user_id
                WHERE i.user_id=? AND p.plan_date BETWEEN ? AND ? AND p.status NOT IN ('CANCELLED','ABANDONED')
                AND i.status<>'SKIPPED'
                """,(r,n)->new PlanSample(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getLong(5)),
                owner,window.from(),window.to());
        long span = java.time.temporal.ChronoUnit.DAYS.between(window.from(), window.to()) + 1;
        var prior = statistics.summary(window.from().minusDays(span), window.from().minusDays(1));
        return new Profile(window, clock.instant(), activity, statistics.dailyTrend(window.from(), window.to()),
                statistics.subjectDistribution(window.from(), window.to()), subjects.list(), goals.list(1, 20, null, null),
                paths.list(1, 20), records.list(1, 10, null, null, window.from(), window.to()), tasks, sample,
                tasks.completionRate(), sample.deviationRatio(), prior.totalMinutes(),
                activity.activeDays() < 2 ? "INSUFFICIENT_SAMPLE" : "OBSERVED");
    }

    private Window window(LocalDate from, LocalDate to, Integer days) {
        LocalDate now = LocalDate.now(clock);
        int span = days == null ? 14 : days;
        if (span < 1 || span > 90) throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        LocalDate end = to == null ? now : to;
        LocalDate start = from == null ? end.minusDays(span - 1) : from;
        if (end.isAfter(now) || start.isAfter(end) || start.isBefore(end.minusDays(89))) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        return new Window(start, end);
    }
}
