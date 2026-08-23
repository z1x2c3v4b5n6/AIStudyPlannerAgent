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
        SELECT i.*, t.title task_title, t.status task_status
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
