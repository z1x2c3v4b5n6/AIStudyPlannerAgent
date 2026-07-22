package com.yhk.aistudyplanner.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import com.yhk.aistudyplanner.plan.entity.StudyPlan;
import com.yhk.aistudyplanner.plan.vo.PlanListView;
import com.yhk.aistudyplanner.plan.vo.PlanTaskCandidate;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate; import java.time.LocalDateTime; import java.util.List;

@Mapper
public interface StudyPlanMapper extends BaseMapper<StudyPlan> {
    @Select("""
        SELECT t.id taskId, t.title taskTitle, t.subject_id subjectId, s.name subjectName,
               s.color subjectColor, t.priority, t.status, t.estimated_minutes estimatedMinutes,
               t.planned_date plannedDate, t.due_at dueAt
        FROM study_task t INNER JOIN subject s ON s.id=t.subject_id AND s.user_id=#{userId}
        WHERE t.user_id=#{userId} AND t.status IN ('TODO','IN_PROGRESS')
          AND (t.planned_date IS NULL OR t.planned_date <= #{planDate})
        ORDER BY CASE WHEN t.due_at IS NOT NULL AND t.due_at < #{dayStart} THEN 0 ELSE 1 END,
                 t.due_at IS NULL, t.due_at ASC, t.priority DESC,
                 t.planned_date IS NULL, t.planned_date ASC,
                 CASE WHEN t.status='IN_PROGRESS' THEN 0 ELSE 1 END, t.id ASC
        """)
    List<PlanTaskCandidate> selectCandidates(@Param("userId") long userId, @Param("planDate") LocalDate planDate,
                                              @Param("dayStart") LocalDateTime dayStart);

    @Select("""
        <script>
        SELECT p.id, p.plan_date planDate, p.available_minutes availableMinutes,
               p.planned_minutes plannedMinutes, p.requirement, p.summary, p.status,
               COUNT(i.id) totalItemCount,
               COALESCE(SUM(i.status='COMPLETED'),0) completedItemCount,
               COALESCE(SUM(i.status='SKIPPED'),0) skippedItemCount,
               COALESCE(SUM(i.status='PENDING'),0) pendingItemCount,
               CASE WHEN COUNT(i.id)=0 THEN 0 ELSE ROUND(SUM(i.status IN ('COMPLETED','SKIPPED'))*100.0/COUNT(i.id),2) END completionPercentage,
               p.created_at createdAt, p.updated_at updatedAt
        FROM study_plan p LEFT JOIN study_plan_item i ON i.plan_id=p.id AND i.user_id=#{userId}
        WHERE p.user_id=#{userId}
        <if test="startDate != null">AND p.plan_date &gt;= #{startDate}</if>
        <if test="endDate != null">AND p.plan_date &lt;= #{endDate}</if>
        <if test="status != null">AND p.status=#{status}</if>
        GROUP BY p.id ORDER BY p.plan_date DESC, p.id DESC
        </script>
        """)
    IPage<PlanListView> selectPlanPage(Page<PlanListView> page, @Param("userId") long userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("status") PlanStatus status);
}
