package com.yhk.aistudyplanner.subject.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.subject.dto.SubjectCreateRequest;
import com.yhk.aistudyplanner.subject.dto.SubjectUpdateRequest;
import com.yhk.aistudyplanner.subject.entity.Subject;
import com.yhk.aistudyplanner.subject.mapper.SubjectMapper;
import com.yhk.aistudyplanner.subject.vo.SubjectView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubjectService {
    private final SubjectMapper subjectMapper;
    private final AuthSessionService sessionService;

    public SubjectService(SubjectMapper subjectMapper, AuthSessionService sessionService) {
        this.subjectMapper = subjectMapper;
        this.sessionService = sessionService;
    }

    public List<SubjectView> list() {
        long userId = sessionService.currentUserId();
        return subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getUserId, userId)
                        .orderByAsc(Subject::getSortOrder, Subject::getCreatedAt))
                .stream().map(SubjectView::from).toList();
    }

    public SubjectView get(long id) {
        return SubjectView.from(requireOwned(id, sessionService.currentUserId()));
    }

    @Transactional
    public SubjectView create(SubjectCreateRequest request) {
        long userId = sessionService.currentUserId();
        String name = request.name().trim();
        ensureNameAvailable(userId, name, null);
        LocalDateTime now = LocalDateTime.now();
        Subject subject = new Subject();
        subject.setUserId(userId);
        subject.setName(name);
        subject.setDescription(trimToNull(request.description()));
        subject.setColor(trimToNull(request.color()));
        subject.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        subject.setCreatedAt(now);
        subject.setUpdatedAt(now);
        try {
            subjectMapper.insert(subject);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.SUBJECT_NAME_EXISTS);
        }
        return SubjectView.from(subject);
    }

    @Transactional
    public SubjectView update(long id, SubjectUpdateRequest request) {
        long userId = sessionService.currentUserId();
        Subject existing = requireOwned(id, userId);
        String name = request.name().trim();
        ensureNameAvailable(userId, name, id);
        existing.setName(name);
        existing.setDescription(trimToNull(request.description()));
        existing.setColor(trimToNull(request.color()));
        existing.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        existing.setUpdatedAt(LocalDateTime.now());
        try {
            int updated = subjectMapper.update(existing, new LambdaUpdateWrapper<Subject>()
                    .eq(Subject::getId, id).eq(Subject::getUserId, userId));
            if (updated != 1) throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.SUBJECT_NAME_EXISTS);
        }
        return SubjectView.from(existing);
    }

    @Transactional
    public void delete(long id) {
        long userId = sessionService.currentUserId();
        requireOwned(id, userId);
        if (subjectMapper.countGoals(userId, id) > 0 || subjectMapper.countTasks(userId, id) > 0
                || subjectMapper.countRecords(userId, id) > 0) {
            throw new BusinessException(ErrorCode.SUBJECT_HAS_REFERENCES);
        }
        int deleted = subjectMapper.delete(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getId, id).eq(Subject::getUserId, userId));
        if (deleted != 1) throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
    }

    public Subject requireOwned(long id, long userId) {
        Subject subject = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getId, id).eq(Subject::getUserId, userId));
        if (subject != null) return subject;
        if (subjectMapper.selectById(id) != null) throw new BusinessException(ErrorCode.SUBJECT_ACCESS_DENIED);
        throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
    }

    private void ensureNameAvailable(long userId, String name, Long excludedId) {
        LambdaQueryWrapper<Subject> query = new LambdaQueryWrapper<Subject>()
                .eq(Subject::getUserId, userId).eq(Subject::getName, name);
        if (excludedId != null) query.ne(Subject::getId, excludedId);
        if (subjectMapper.selectCount(query) > 0) throw new BusinessException(ErrorCode.SUBJECT_NAME_EXISTS);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

