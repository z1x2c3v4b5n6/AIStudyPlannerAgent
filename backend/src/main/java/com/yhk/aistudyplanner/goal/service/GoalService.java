package com.yhk.aistudyplanner.goal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.goal.dto.GoalCreateRequest;
import com.yhk.aistudyplanner.goal.dto.GoalStatusRequest;
import com.yhk.aistudyplanner.goal.dto.GoalUpdateRequest;
import com.yhk.aistudyplanner.goal.entity.GoalStatus;
import com.yhk.aistudyplanner.goal.entity.StudyGoal;
import com.yhk.aistudyplanner.goal.mapper.GoalMapper;
import com.yhk.aistudyplanner.goal.vo.GoalDetailView;
import com.yhk.aistudyplanner.goal.vo.GoalView;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class GoalService {
    private final GoalMapper goalMapper;
    private final SubjectService subjectService;
    private final AuthSessionService sessionService;

    public GoalService(GoalMapper goalMapper, SubjectService subjectService, AuthSessionService sessionService) {
        this.goalMapper = goalMapper;
        this.subjectService = subjectService;
        this.sessionService = sessionService;
    }

    public PageResponse<GoalView> list(long page, long pageSize, Long subjectId, GoalStatus status) {
        long userId = sessionService.currentUserId();
        if (subjectId != null) subjectService.requireOwned(subjectId, userId);
        LambdaQueryWrapper<StudyGoal> query = new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getUserId, userId)
                .eq(subjectId != null, StudyGoal::getSubjectId, subjectId)
                .eq(status != null, StudyGoal::getStatus, status)
                .orderByDesc(StudyGoal::getCreatedAt);
        Page<StudyGoal> result = goalMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResponse<>(result.getRecords().stream().map(GoalView::from).toList(), page, pageSize, result.getTotal());
    }

    public GoalDetailView get(long id) {
        long userId = sessionService.currentUserId();
        StudyGoal goal = requireOwned(id, userId);
        long total = goalMapper.countTasks(userId, id);
        long completed = goalMapper.countCompletedTasks(userId, id);
        double rate = total == 0 ? 0D : Math.round(completed * 10000D / total) / 100D;
        return new GoalDetailView(GoalView.from(goal), total, completed, rate);
    }

    @Transactional
    public GoalView create(GoalCreateRequest request) {
        long userId = sessionService.currentUserId();
        subjectService.requireOwned(request.subjectId(), userId);
        LocalDateTime now = LocalDateTime.now();
        StudyGoal goal = new StudyGoal();
        goal.setUserId(userId);
        goal.setSubjectId(request.subjectId());
        goal.setTitle(request.title().trim());
        goal.setDescription(trimToNull(request.description()));
        goal.setTargetMinutes(request.targetMinutes());
        goal.setTargetDate(request.targetDate());
        goal.setStatus(request.status());
        goal.setCreatedAt(now);
        goal.setUpdatedAt(now);
        goalMapper.insert(goal);
        return GoalView.from(goal);
    }

    @Transactional
    public GoalView update(long id, GoalUpdateRequest request) {
        long userId = sessionService.currentUserId();
        StudyGoal goal = requireOwned(id, userId);
        subjectService.requireOwned(request.subjectId(), userId);
        goal.setSubjectId(request.subjectId());
        goal.setTitle(request.title().trim());
        goal.setDescription(trimToNull(request.description()));
        goal.setTargetMinutes(request.targetMinutes());
        goal.setTargetDate(request.targetDate());
        goal.setUpdatedAt(LocalDateTime.now());
        updateOwned(goal, userId);
        return GoalView.from(goal);
    }

    @Transactional
    public GoalView changeStatus(long id, GoalStatusRequest request) {
        long userId = sessionService.currentUserId();
        StudyGoal goal = requireOwned(id, userId);
        goal.setStatus(request.status());
        goal.setUpdatedAt(LocalDateTime.now());
        updateOwned(goal, userId);
        return GoalView.from(goal);
    }

    @Transactional
    public void delete(long id) {
        long userId = sessionService.currentUserId();
        requireOwned(id, userId);
        if (goalMapper.countTasks(userId, id) > 0) throw new BusinessException(ErrorCode.GOAL_HAS_TASKS);
        int deleted = goalMapper.delete(new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getId, id).eq(StudyGoal::getUserId, userId));
        if (deleted != 1) throw new BusinessException(ErrorCode.GOAL_NOT_FOUND);
    }

    public StudyGoal requireOwned(long id, long userId) {
        StudyGoal goal = goalMapper.selectOne(new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getId, id).eq(StudyGoal::getUserId, userId));
        if (goal != null) return goal;
        if (goalMapper.selectById(id) != null) throw new BusinessException(ErrorCode.GOAL_ACCESS_DENIED);
        throw new BusinessException(ErrorCode.GOAL_NOT_FOUND);
    }

    private void updateOwned(StudyGoal goal, long userId) {
        int updated = goalMapper.update(goal, new LambdaUpdateWrapper<StudyGoal>()
                .eq(StudyGoal::getId, goal.getId()).eq(StudyGoal::getUserId, userId));
        if (updated != 1) throw new BusinessException(ErrorCode.GOAL_NOT_FOUND);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

