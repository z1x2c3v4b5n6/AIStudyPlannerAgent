package com.yhk.aistudyplanner.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yhk.aistudyplanner.subject.entity.Subject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubjectMapper extends BaseMapper<Subject> {
    @Select("SELECT COUNT(*) FROM study_goal WHERE user_id = #{userId} AND subject_id = #{subjectId}")
    long countGoals(@Param("userId") long userId, @Param("subjectId") long subjectId);

    @Select("SELECT COUNT(*) FROM study_task WHERE user_id = #{userId} AND subject_id = #{subjectId}")
    long countTasks(@Param("userId") long userId, @Param("subjectId") long subjectId);

    @Select("SELECT COUNT(*) FROM study_record WHERE user_id = #{userId} AND subject_id = #{subjectId}")
    long countRecords(@Param("userId") long userId, @Param("subjectId") long subjectId);
}

