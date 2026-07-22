package com.yhk.aistudyplanner.statistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StatisticsMapper {
    record SummaryAggregate(long totalMinutes, long recordCount, long activeDays) {}
    record DailyAggregate(LocalDate date, long totalMinutes, long recordCount) {}
    record SubjectAggregate(Long subjectId, String subjectName, String subjectColor,
                            long totalMinutes, long recordCount) {}

    @Select("""
            SELECT COALESCE(SUM(duration_minutes), 0) AS totalMinutes,
                   COUNT(*) AS recordCount,
                   COUNT(DISTINCT DATE(started_at)) AS activeDays
            FROM study_record
            WHERE user_id = #{userId}
              AND started_at >= #{from}
              AND started_at < #{toExclusive}
            """)
    SummaryAggregate selectSummary(@Param("userId") long userId,
                                   @Param("from") LocalDateTime from,
                                   @Param("toExclusive") LocalDateTime toExclusive);

    @Select("""
            SELECT DATE(started_at) AS date,
                   SUM(duration_minutes) AS totalMinutes,
                   COUNT(*) AS recordCount
            FROM study_record
            WHERE user_id = #{userId}
              AND started_at >= #{from}
              AND started_at < #{toExclusive}
            GROUP BY DATE(started_at)
            ORDER BY DATE(started_at) ASC
            """)
    List<DailyAggregate> selectDailyTrend(@Param("userId") long userId,
                                          @Param("from") LocalDateTime from,
                                          @Param("toExclusive") LocalDateTime toExclusive);

    @Select("""
            SELECT s.id AS subjectId, s.name AS subjectName, s.color AS subjectColor,
                   SUM(r.duration_minutes) AS totalMinutes,
                   COUNT(*) AS recordCount
            FROM study_record r
            INNER JOIN subject s ON s.id = r.subject_id AND s.user_id = #{userId}
            WHERE r.user_id = #{userId}
              AND r.started_at >= #{from}
              AND r.started_at < #{toExclusive}
            GROUP BY s.id, s.name, s.color
            ORDER BY totalMinutes DESC, s.id ASC
            """)
    List<SubjectAggregate> selectSubjectDistribution(@Param("userId") long userId,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("toExclusive") LocalDateTime toExclusive);
}
