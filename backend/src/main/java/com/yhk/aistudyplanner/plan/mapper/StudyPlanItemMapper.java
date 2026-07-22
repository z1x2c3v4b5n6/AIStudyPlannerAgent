package com.yhk.aistudyplanner.plan.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yhk.aistudyplanner.plan.entity.StudyPlanItem;
import com.yhk.aistudyplanner.plan.vo.PlanItemView;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface StudyPlanItemMapper extends BaseMapper<StudyPlanItem> {
    @Select("""
        SELECT i.id, i.sequence_no sequenceNo, i.task_id taskId, t.title taskTitle,
               t.subject_id subjectId, s.name subjectName, s.color subjectColor,
               i.start_at startAt, i.end_at endAt, i.planned_minutes plannedMinutes,
               i.reason, i.status
        FROM study_plan_item i
        INNER JOIN study_task t ON t.id=i.task_id AND t.user_id=#{userId}
        INNER JOIN subject s ON s.id=t.subject_id AND s.user_id=#{userId}
        WHERE i.plan_id=#{planId} AND i.user_id=#{userId}
        ORDER BY i.sequence_no ASC
        """)
    List<PlanItemView> selectViews(@Param("planId") long planId, @Param("userId") long userId);
}
