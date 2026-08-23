package com.yhk.aistudyplanner.learningpath.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yhk.aistudyplanner.learningpath.entity.StudyPathItem;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LearningPathItemMapper extends BaseMapper<StudyPathItem> {
    @Select("SELECT * FROM study_path_item WHERE id=#{itemId} AND path_id=#{pathId} AND user_id=#{userId} FOR UPDATE")
    StudyPathItem selectOwnedForUpdate(@Param("pathId") long pathId, @Param("itemId") long itemId,
                                      @Param("userId") long userId);

    @Select("""
        SELECT i.id AS id,
               i.sequence_no AS sequenceNo,
               i.stage_no AS stageNo,
               i.stage_title AS stageTitle,
               i.stage_description AS stageDescription,
               i.topic AS topic,
               i.learning_objective AS learningObjective,
               i.learning_method AS learningMethod,
               i.completion_criteria AS completionCriteria,
               i.estimated_minutes AS estimatedMinutes,
               i.suggested_day AS suggestedDay,
               i.prerequisite_text AS prerequisiteText,
               i.reason AS reason,
               i.status AS status,
               i.task_id AS taskId,
               t.title AS taskTitle,
               t.status AS taskStatus
        FROM study_path_item i
        LEFT JOIN study_task t ON t.id=i.task_id AND t.user_id=#{userId}
        WHERE i.path_id=#{pathId} AND i.user_id=#{userId}
        ORDER BY i.sequence_no ASC
        """)
    List<ItemRow> selectRows(@Param("pathId") long pathId, @Param("userId") long userId);

    record ItemRow(Long id, Integer sequenceNo, Integer stageNo, String stageTitle,
            String stageDescription, String topic, String learningObjective,
            String learningMethod, String completionCriteria, Integer estimatedMinutes,
            Integer suggestedDay, String prerequisiteText, String reason, String status,
            Long taskId, String taskTitle, String taskStatus) {}
}
