package com.yhk.aistudyplanner.ai.mapper;

import com.yhk.aistudyplanner.ai.vo.SelectableTaskView;
import com.yhk.aistudyplanner.ai.vo.SubjectMatchData;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlanningSelectionMapper {
  @Select("SELECT id FROM `user` WHERE id=#{userId} FOR UPDATE")
  Long lockUser(@Param("userId") long userId);

  @Select(
      """
      SELECT s.id subjectId, s.name subjectName, s.color subjectColor,
             SUM(CASE WHEN t.status IN ('TODO','IN_PROGRESS') THEN 1 ELSE 0 END) pendingTaskCount
      FROM subject s
      LEFT JOIN study_task t ON t.subject_id=s.id AND t.user_id=#{userId}
      WHERE s.user_id=#{userId}
      GROUP BY s.id,s.name,s.color,s.sort_order
      ORDER BY s.sort_order,s.id
      """)
  List<SubjectMatchData> subjects(@Param("userId") long userId);

  @Select(
      """
      <script>
      SELECT t.id taskId,t.title taskTitle,t.subject_id subjectId,s.name subjectName,
             s.color subjectColor,g.title goalTitle,t.estimated_minutes estimatedMinutes,
             t.due_at deadline,t.priority,t.status,
             CASE
               WHEN t.status='IN_PROGRESS' THEN '进行中的任务'
               WHEN t.due_at IS NOT NULL AND t.due_at &lt; #{planDate} THEN '任务已逾期'
               WHEN t.due_at IS NOT NULL AND t.due_at &lt; #{planDatePlusThree} THEN '任务即将截止'
               WHEN t.priority &gt;= 3 THEN '高优先级任务'
               WHEN g.id IS NOT NULL THEN '关联当前学习目标'
               ELSE '所选科目的待办任务'
             END matchReason,
             CASE WHEN t.status='IN_PROGRESS' OR t.priority &gt;= 3
                    OR (t.due_at IS NOT NULL AND t.due_at &lt; #{planDatePlusThree})
                  THEN TRUE ELSE FALSE END recommended
      FROM study_task t
      JOIN subject s ON s.id=t.subject_id AND s.user_id=#{userId}
      LEFT JOIN study_goal g ON g.id=t.goal_id AND g.user_id=#{userId}
      WHERE t.user_id=#{userId}
        AND t.status IN ('TODO','IN_PROGRESS')
        AND (t.planned_date IS NULL OR t.planned_date &lt;= #{planDate})
        AND t.subject_id IN
        <foreach collection="subjectIds" item="id" open="(" separator="," close=")">#{id}</foreach>
      ORDER BY recommended DESC,t.due_at IS NULL,t.due_at,t.priority DESC,
               CASE WHEN t.status='IN_PROGRESS' THEN 0 ELSE 1 END,t.id
      </script>
      """)
  List<SelectableTaskView> tasks(
      @Param("userId") long userId,
      @Param("subjectIds") List<Long> subjectIds,
      @Param("planDate") LocalDate planDate,
      @Param("planDatePlusThree") LocalDate planDatePlusThree);
}
