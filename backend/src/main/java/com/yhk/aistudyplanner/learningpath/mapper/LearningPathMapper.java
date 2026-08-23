package com.yhk.aistudyplanner.learningpath.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.learningpath.entity.StudyPath;
import com.yhk.aistudyplanner.learningpath.vo.LearningPathListView;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LearningPathMapper extends BaseMapper<StudyPath> {
    @Select("SELECT * FROM study_path WHERE id=#{id} AND user_id=#{userId} FOR UPDATE")
    StudyPath selectOwnedForUpdate(@Param("id") long id, @Param("userId") long userId);

    @Select("""
        SELECT p.id, p.subject_id subjectId, s.name subjectName, s.color subjectColor,
               p.title, CASE WHEN p.status='ACTIVE' AND COUNT(i.id)>0
                    AND SUM(CASE WHEN i.task_id IS NOT NULL AND t.status='COMPLETED' THEN 1 ELSE 0 END)=COUNT(i.id)
                    THEN 'COMPLETED' ELSE p.status END status, p.start_date startDate, p.target_date targetDate,
               COUNT(DISTINCT i.stage_no) stageCount, COUNT(i.id) totalItems,
               COALESCE(SUM(CASE WHEN i.task_id IS NOT NULL AND t.status='COMPLETED' THEN 1 ELSE 0 END),0) completedItems,
               COALESCE(SUM(CASE WHEN i.task_id IS NULL AND i.status='SKIPPED' THEN 1 ELSE 0 END),0) skippedItems,
               CASE WHEN COUNT(i.id)=0 THEN 0 ELSE ROUND(
                 SUM(CASE WHEN i.task_id IS NOT NULL AND t.status='COMPLETED' THEN 1 ELSE 0 END)*100.0/COUNT(i.id),2) END progress,
               SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN NOT (i.task_id IS NOT NULL AND t.status='COMPLETED')
                         AND NOT (i.task_id IS NULL AND i.status='SKIPPED') THEN i.stage_title END ORDER BY i.sequence_no),',',1) currentStage,
               p.created_at createdAt
        FROM study_path p
        LEFT JOIN subject s ON s.id=p.subject_id AND s.user_id=#{userId}
        LEFT JOIN study_path_item i ON i.path_id=p.id AND i.user_id=#{userId}
        LEFT JOIN study_task t ON t.id=i.task_id AND t.user_id=#{userId}
        WHERE p.user_id=#{userId}
        GROUP BY p.id
        ORDER BY p.updated_at DESC, p.id DESC
        """)
    IPage<LearningPathListView> selectPageView(Page<LearningPathListView> page, @Param("userId") long userId);
}
