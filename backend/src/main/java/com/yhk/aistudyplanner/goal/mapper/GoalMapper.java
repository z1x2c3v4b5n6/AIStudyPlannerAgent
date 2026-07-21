package com.yhk.aistudyplanner.goal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yhk.aistudyplanner.goal.entity.StudyGoal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GoalMapper extends BaseMapper<StudyGoal> {
    @Select("SELECT COUNT(*) FROM study_task WHERE user_id = #{userId} AND goal_id = #{goalId}")
    long countTasks(@Param("userId") long userId, @Param("goalId") long goalId);

    @Select("SELECT COUNT(*) FROM study_task WHERE user_id = #{userId} AND goal_id = #{goalId} AND status = 'COMPLETED'")
    long countCompletedTasks(@Param("userId") long userId, @Param("goalId") long goalId);
}

