package com.yhk.aistudyplanner.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<StudyTask> {
    @Select("SELECT * FROM study_task WHERE user_id = #{userId} AND planned_date = #{date} " +
            "AND status IN ('TODO','IN_PROGRESS') ORDER BY priority DESC, due_at ASC, created_at DESC")
    List<StudyTask> selectToday(@Param("userId") long userId, @Param("date") LocalDate date);

    @Select("SELECT * FROM study_task WHERE user_id = #{userId} AND due_at >= #{from} AND due_at <= #{to} " +
            "AND status IN ('TODO','IN_PROGRESS') ORDER BY due_at ASC, priority DESC")
    List<StudyTask> selectUpcoming(@Param("userId") long userId, @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    @Select("SELECT COUNT(*) FROM study_record WHERE user_id = #{userId} AND task_id = #{taskId}")
    long countRecords(@Param("userId") long userId, @Param("taskId") long taskId);

    @Select("SELECT COUNT(*) FROM study_plan_item WHERE user_id = #{userId} AND task_id = #{taskId}")
    long countPlanItems(@Param("userId") long userId, @Param("taskId") long taskId);
}

