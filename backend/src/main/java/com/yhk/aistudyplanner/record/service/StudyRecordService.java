package com.yhk.aistudyplanner.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.record.dto.RecordCreateRequest;
import com.yhk.aistudyplanner.record.dto.RecordUpdateRequest;
import com.yhk.aistudyplanner.record.entity.StudyRecord;
import com.yhk.aistudyplanner.record.mapper.StudyRecordMapper;
import com.yhk.aistudyplanner.record.vo.StudyRecordView;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class StudyRecordService {
    private final StudyRecordMapper recordMapper;
    private final TaskMapper taskMapper;
    private final SubjectService subjectService;
    private final AuthSessionService sessionService;
    private final Clock clock;

    public StudyRecordService(StudyRecordMapper recordMapper, TaskMapper taskMapper, SubjectService subjectService,
                              AuthSessionService sessionService, Clock clock) {
        this.recordMapper = recordMapper;
        this.taskMapper = taskMapper;
        this.subjectService = subjectService;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudyRecordView> list(long page, long pageSize, Long subjectId, Long taskId,
                                              LocalDate startDate, LocalDate endDate) {
        long userId = sessionService.currentUserId();
        validateDateOrder(startDate, endDate);
        if (subjectId != null) subjectService.requireOwned(subjectId, userId);
        StudyTask task = taskId == null ? null : requireOwnedTask(taskId, userId);
        if (task != null && subjectId != null && !subjectId.equals(task.getSubjectId())) {
            throw new BusinessException(ErrorCode.RECORD_SUBJECT_MISMATCH);
        }
        LocalDateTime from = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime toExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        IPage<StudyRecordView> result = recordMapper.selectRecordPage(
                new Page<>(page, pageSize), userId, subjectId, taskId, from, toExclusive);
        return new PageResponse<>(result.getRecords(), page, pageSize, result.getTotal());
    }

    @Transactional(readOnly = true)
    public StudyRecordView get(long id) {
        long userId = sessionService.currentUserId();
        requireOwned(id, userId);
        StudyRecordView view = recordMapper.selectView(id, userId);
        if (view == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        return view;
    }

    @Transactional
    public StudyRecordView create(RecordCreateRequest request) {
        long userId = sessionService.currentUserId();
        validateAssociations(userId, request.subjectId(), request.taskId());
        int duration = validateAndCalculateDuration(request.startedAt(), request.endedAt());
        ensureNoOverlap(userId, request.startedAt(), request.endedAt(), null);
        LocalDateTime now = LocalDateTime.now(clock);
        StudyRecord record = new StudyRecord();
        record.setUserId(userId);
        applyFields(record, request.subjectId(), request.taskId(), request.startedAt(), request.endedAt(),
                duration, request.feedback());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        recordMapper.insert(record);
        return recordMapper.selectView(record.getId(), userId);
    }

    @Transactional
    public StudyRecordView update(long id, RecordUpdateRequest request) {
        long userId = sessionService.currentUserId();
        requireOwned(id, userId);
        validateAssociations(userId, request.subjectId(), request.taskId());
        int duration = validateAndCalculateDuration(request.startedAt(), request.endedAt());
        ensureNoOverlap(userId, request.startedAt(), request.endedAt(), id);
        LocalDateTime now = LocalDateTime.now(clock);
        LambdaUpdateWrapper<StudyRecord> wrapper = new LambdaUpdateWrapper<StudyRecord>()
                .eq(StudyRecord::getId, id)
                .eq(StudyRecord::getUserId, userId)
                .set(StudyRecord::getSubjectId, request.subjectId())
                .set(StudyRecord::getTaskId, request.taskId())
                .set(StudyRecord::getStartedAt, request.startedAt())
                .set(StudyRecord::getEndedAt, request.endedAt())
                .set(StudyRecord::getDurationMinutes, duration)
                .set(StudyRecord::getFeedback, trimToNull(request.feedback()))
                .set(StudyRecord::getUpdatedAt, now);
        int updated = recordMapper.update(null, wrapper);
        if (updated != 1) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        return recordMapper.selectView(id, userId);
    }

    @Transactional
    public void delete(long id) {
        long userId = sessionService.currentUserId();
        requireOwned(id, userId);
        int deleted = recordMapper.delete(new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getId, id).eq(StudyRecord::getUserId, userId));
        if (deleted != 1) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
    }

    private void validateAssociations(long userId, long subjectId, Long taskId) {
        subjectService.requireOwned(subjectId, userId);
        if (taskId == null) return;
        StudyTask task = requireOwnedTask(taskId, userId);
        if (!Long.valueOf(subjectId).equals(task.getSubjectId())) {
            throw new BusinessException(ErrorCode.RECORD_SUBJECT_MISMATCH);
        }
    }

    private StudyTask requireOwnedTask(long taskId, long userId) {
        StudyTask task = taskMapper.selectOne(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getId, taskId).eq(StudyTask::getUserId, userId));
        if (task != null) return task;
        if (taskMapper.selectById(taskId) != null) throw new BusinessException(ErrorCode.TASK_ACCESS_DENIED);
        throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    }

    private StudyRecord requireOwned(long id, long userId) {
        StudyRecord record = recordMapper.selectOne(new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getId, id).eq(StudyRecord::getUserId, userId));
        if (record != null) return record;
        if (recordMapper.selectById(id) != null) throw new BusinessException(ErrorCode.RECORD_ACCESS_DENIED);
        throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
    }

    private int validateAndCalculateDuration(LocalDateTime startedAt, LocalDateTime endedAt) {
        if (!endedAt.isAfter(startedAt)) throw new BusinessException(ErrorCode.INVALID_RECORD_TIME);
        if (endedAt.isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.RECORD_END_TIME_IN_FUTURE);
        }
        if (endedAt.isAfter(startedAt.plusMinutes(1440))) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_DURATION);
        }
        if (!startedAt.toLocalDate().equals(endedAt.toLocalDate())) {
            throw new BusinessException(ErrorCode.RECORD_CROSSES_DAY);
        }
        long duration = ChronoUnit.MINUTES.between(startedAt, endedAt);
        if (duration < 1 || duration > 1440) throw new BusinessException(ErrorCode.INVALID_RECORD_DURATION);
        return Math.toIntExact(duration);
    }

    private void ensureNoOverlap(long userId, LocalDateTime startedAt, LocalDateTime endedAt, Long excludedId) {
        if (recordMapper.countOverlapping(userId, startedAt, endedAt, excludedId) > 0) {
            throw new BusinessException(ErrorCode.RECORD_TIME_OVERLAP);
        }
    }

    private void validateDateOrder(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
    }

    private void applyFields(StudyRecord record, long subjectId, Long taskId, LocalDateTime startedAt,
                             LocalDateTime endedAt, int duration, String feedback) {
        record.setSubjectId(subjectId);
        record.setTaskId(taskId);
        record.setStartedAt(startedAt);
        record.setEndedAt(endedAt);
        record.setDurationMinutes(duration);
        record.setFeedback(trimToNull(feedback));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
