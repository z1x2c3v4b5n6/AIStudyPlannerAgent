package com.yhk.aistudyplanner.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.record.entity.StudyRecord;
import com.yhk.aistudyplanner.record.vo.StudyRecordView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface StudyRecordMapper extends BaseMapper<StudyRecord> {
    @Select("""
            <script>
            SELECT r.id, r.subject_id AS subjectId, s.name AS subjectName, s.color AS subjectColor,
                   r.task_id AS taskId, t.title AS taskTitle, r.started_at AS startedAt,
                   r.ended_at AS endedAt, r.duration_minutes AS durationMinutes,
                   r.feedback, r.created_at AS createdAt, r.updated_at AS updatedAt
            FROM study_record r
            INNER JOIN subject s ON s.id = r.subject_id AND s.user_id = #{userId}
            LEFT JOIN study_task t ON t.id = r.task_id AND t.user_id = #{userId}
            WHERE r.user_id = #{userId}
            <if test="subjectId != null">AND r.subject_id = #{subjectId}</if>
            <if test="taskId != null">AND r.task_id = #{taskId}</if>
            <if test="from != null">AND r.started_at &gt;= #{from}</if>
            <if test="toExclusive != null">AND r.started_at &lt; #{toExclusive}</if>
            ORDER BY r.started_at DESC, r.id DESC
            </script>
            """)
    IPage<StudyRecordView> selectRecordPage(Page<StudyRecordView> page,
                                             @Param("userId") long userId,
                                             @Param("subjectId") Long subjectId,
                                             @Param("taskId") Long taskId,
                                             @Param("from") LocalDateTime from,
                                             @Param("toExclusive") LocalDateTime toExclusive);

    @Select("""
            SELECT r.id, r.subject_id AS subjectId, s.name AS subjectName, s.color AS subjectColor,
                   r.task_id AS taskId, t.title AS taskTitle, r.started_at AS startedAt,
                   r.ended_at AS endedAt, r.duration_minutes AS durationMinutes,
                   r.feedback, r.created_at AS createdAt, r.updated_at AS updatedAt
            FROM study_record r
            INNER JOIN subject s ON s.id = r.subject_id AND s.user_id = #{userId}
            LEFT JOIN study_task t ON t.id = r.task_id AND t.user_id = #{userId}
            WHERE r.id = #{id} AND r.user_id = #{userId}
            """)
    StudyRecordView selectView(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM study_record
            WHERE user_id = #{userId}
              AND started_at &lt; #{endedAt}
              AND ended_at &gt; #{startedAt}
            <if test="excludedId != null">AND id != #{excludedId}</if>
            </script>
            """)
    long countOverlapping(@Param("userId") long userId,
                          @Param("startedAt") LocalDateTime startedAt,
                          @Param("endedAt") LocalDateTime endedAt,
                          @Param("excludedId") Long excludedId);
}
