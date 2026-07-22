package com.yhk.aistudyplanner.ai.mapper;
import com.yhk.aistudyplanner.ai.vo.AiPlanningContext.*;import org.apache.ibatis.annotations.*;import java.time.*;import java.util.List;
@Mapper public interface AiPlanningContextMapper {
 @Select("SELECT id,name,color FROM subject WHERE user_id=#{userId} ORDER BY sort_order,id") List<SubjectContext> subjects(@Param("userId")long userId);
 @Select("SELECT id,subject_id subjectId,title,target_date targetDate FROM study_goal WHERE user_id=#{userId} AND status='ACTIVE' ORDER BY target_date IS NULL,target_date,id LIMIT 100") List<GoalContext> goals(@Param("userId")long userId);
 @Select("""
 SELECT t.id,t.subject_id subjectId,s.name subjectName,s.color subjectColor,t.goal_id goalId,g.title goalTitle,t.title,t.description,t.priority,t.status,t.estimated_minutes estimatedMinutes,t.planned_date plannedDate,t.due_at dueAt
 FROM study_task t JOIN subject s ON s.id=t.subject_id AND s.user_id=#{userId} LEFT JOIN study_goal g ON g.id=t.goal_id AND g.user_id=#{userId}
 WHERE t.user_id=#{userId} AND t.status IN ('TODO','IN_PROGRESS')
 ORDER BY CASE WHEN t.due_at IS NOT NULL AND t.due_at < #{dayStart} THEN 0 ELSE 1 END,t.due_at IS NULL,t.due_at,t.priority DESC,t.planned_date IS NULL,t.planned_date,CASE WHEN t.status='IN_PROGRESS' THEN 0 ELSE 1 END,t.id LIMIT 100
 """) List<TaskContext> tasks(@Param("userId")long userId,@Param("planDate")LocalDate planDate,@Param("dayStart")LocalDateTime dayStart);
 @Select("SELECT COALESCE(SUM(duration_minutes),0) totalMinutes,COUNT(*) recordCount,COUNT(DISTINCT DATE(started_at)) activeDays FROM study_record WHERE user_id=#{userId} AND started_at>=#{from} AND started_at<#{to}") StudySummaryContext summary(@Param("userId")long userId,@Param("from")LocalDateTime from,@Param("to")LocalDateTime to);
 @Select("SELECT s.id subjectId,s.name subjectName,SUM(r.duration_minutes) totalMinutes FROM study_record r JOIN subject s ON s.id=r.subject_id AND s.user_id=#{userId} WHERE r.user_id=#{userId} AND r.started_at>=#{from} AND r.started_at<#{to} GROUP BY s.id,s.name ORDER BY totalMinutes DESC,s.id") List<SubjectStudyContext> subjectStudy(@Param("userId")long userId,@Param("from")LocalDateTime from,@Param("to")LocalDateTime to);
 @Select("SELECT r.subject_id subjectId,s.name subjectName,r.task_id taskId,t.title taskTitle,r.started_at startedAt,r.duration_minutes durationMinutes,r.feedback FROM study_record r JOIN subject s ON s.id=r.subject_id AND s.user_id=#{userId} LEFT JOIN study_task t ON t.id=r.task_id AND t.user_id=#{userId} WHERE r.user_id=#{userId} ORDER BY r.started_at DESC,r.id DESC LIMIT 30") List<RecordContext> records(@Param("userId")long userId);
}
