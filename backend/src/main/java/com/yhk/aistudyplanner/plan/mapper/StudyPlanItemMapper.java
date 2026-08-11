package com.yhk.aistudyplanner.plan.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yhk.aistudyplanner.plan.entity.StudyPlanItem;
import com.yhk.aistudyplanner.plan.vo.PlanItemView;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface StudyPlanItemMapper extends BaseMapper<StudyPlanItem> {
    @Select("""
        SELECT * FROM study_plan_item
        WHERE id=#{itemId} AND plan_id=#{planId} AND user_id=#{userId}
        FOR UPDATE
        """)
    StudyPlanItem selectOwnedForUpdate(@Param("planId") long planId,
                                       @Param("itemId") long itemId,
                                       @Param("userId") long userId);

    @Select("""
        SELECT i.id, i.sequence_no sequenceNo, i.task_id taskId, t.title taskTitle,
               t.subject_id subjectId, s.name subjectName, s.color subjectColor,
               i.start_at startAt, i.end_at endAt, i.planned_minutes plannedMinutes,
               r.duration_minutes actualMinutes, r.feedback,
               i.reason, i.status
        FROM study_plan_item i
        INNER JOIN study_task t ON t.id=i.task_id AND t.user_id=#{userId}
        INNER JOIN subject s ON s.id=t.subject_id AND s.user_id=#{userId}
        LEFT JOIN study_record r ON r.plan_item_id=i.id AND r.plan_id=i.plan_id
            AND r.user_id=#{userId}
        WHERE i.plan_id=#{planId} AND i.user_id=#{userId}
        ORDER BY i.sequence_no ASC
        """)
    List<PlanItemView> selectViews(@Param("planId") long planId, @Param("userId") long userId);
}
