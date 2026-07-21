package com.yhk.aistudyplanner.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.goal.entity.StudyGoal;
import com.yhk.aistudyplanner.goal.service.GoalService;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.dto.TaskCreateRequest;
import com.yhk.aistudyplanner.task.dto.TaskStatusRequest;
import com.yhk.aistudyplanner.task.dto.TaskUpdateRequest;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import com.yhk.aistudyplanner.task.vo.TaskView;
import com.yhk.aistudyplanner.task.vo.TodayTasksView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Service
public class TaskService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskMapper taskMapper;
    private final SubjectService subjectService;
    private final GoalService goalService;
    private final AuthSessionService sessionService;

    public TaskService(TaskMapper taskMapper, SubjectService subjectService, GoalService goalService,
                       AuthSessionService sessionService) {
        this.taskMapper = taskMapper;
        this.subjectService = subjectService;
        this.goalService = goalService;
        this.sessionService = sessionService;
    }

    public PageResponse<TaskView> list(long page, long pageSize, Long subjectId, Long goalId, TaskStatus status,
                                       Integer priority, LocalDate plannedDate) {
        long userId = sessionService.currentUserId();
        if (subjectId != null) subjectService.requireOwned(subjectId, userId);
        if (goalId != null) goalService.requireOwned(goalId, userId);
        LambdaQueryWrapper<StudyTask> query = new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId)
                .eq(subjectId != null, StudyTask::getSubjectId, subjectId)
                .eq(goalId != null, StudyTask::getGoalId, goalId)
                .eq(status != null, StudyTask::getStatus, status)
                .eq(priority != null, StudyTask::getPriority, priority)
                .eq(plannedDate != null, StudyTask::getPlannedDate, plannedDate)
                .orderByDesc(StudyTask::getPriority)
                .orderByAsc(StudyTask::getDueAt)
                .orderByDesc(StudyTask::getCreatedAt);
        Page<StudyTask> result = taskMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResponse<>(result.getRecords().stream().map(TaskView::from).toList(), page, pageSize, result.getTotal());
    }

    public TaskView get(long id) {
        return TaskView.from(requireOwned(id, sessionService.currentUserId()));
    }

    public TodayTasksView today(LocalDate requestedDate) {
        long userId = sessionService.currentUserId();
        LocalDate date = requestedDate == null ? LocalDate.now(BUSINESS_ZONE) : requestedDate;
        List<TaskView> tasks = taskMapper.selectToday(userId, date).stream().map(TaskView::from).toList();
        int total = tasks.stream().mapToInt(TaskView::estimatedMinutes).sum();
        return new TodayTasksView(tasks, total);
    }

    public List<TaskView> upcoming(int days) {
        long userId = sessionService.currentUserId();
        LocalDateTime from = LocalDateTime.now(BUSINESS_ZONE);
        return taskMapper.selectUpcoming(userId, from, from.plusDays(days)).stream().map(TaskView::from).toList();
    }

    @Transactional
    public TaskView create(TaskCreateRequest request) {
        long userId = sessionService.currentUserId();
        validateAssociations(userId, request.subjectId(), request.goalId());
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        StudyTask task = new StudyTask();
        task.setUserId(userId);
        applyEditableFields(task, request.subjectId(), request.goalId(), request.title(), request.description(),
                request.priority(), request.estimatedMinutes(), request.plannedDate(), request.dueAt());
        task.setStatus(TaskStatus.TODO);
        task.setCompletedAt(null);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return TaskView.from(task);
    }

    @Transactional
    public TaskView update(long id, TaskUpdateRequest request) {
        long userId = sessionService.currentUserId();
        StudyTask task = requireOwned(id, userId);
        validateAssociations(userId, request.subjectId(), request.goalId());
        applyEditableFields(task, request.subjectId(), request.goalId(), request.title(), request.description(),
                request.priority(), request.estimatedMinutes(), request.plannedDate(), request.dueAt());
        task.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
        updateOwned(task, userId);
        return TaskView.from(task);
    }

    @Transactional
    public TaskView changeStatus(long id, TaskStatusRequest request) {
        long userId = sessionService.currentUserId();
        StudyTask task = requireOwned(id, userId);
        task.setStatus(request.status());
        task.setCompletedAt(request.status() == TaskStatus.COMPLETED ? LocalDateTime.now(BUSINESS_ZONE) : null);
        task.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
        updateOwned(task, userId);
        return TaskView.from(task);
    }

    @Transactional
    public void delete(long id) {
        long userId = sessionService.currentUserId();
        requireOwned(id, userId);
        if (taskMapper.countRecords(userId, id) > 0 || taskMapper.countPlanItems(userId, id) > 0) {
            throw new BusinessException(ErrorCode.TASK_HAS_REFERENCES);
        }
        int deleted = taskMapper.delete(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getId, id).eq(StudyTask::getUserId, userId));
        if (deleted != 1) throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    }

    private StudyTask requireOwned(long id, long userId) {
        StudyTask task = taskMapper.selectOne(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getId, id).eq(StudyTask::getUserId, userId));
        if (task == null) throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        return task;
    }

    private void validateAssociations(long userId, long subjectId, Long goalId) {
        subjectService.requireOwned(subjectId, userId);
        if (goalId == null) return;
        StudyGoal goal = goalService.requireOwned(goalId, userId);
        if (!Long.valueOf(subjectId).equals(goal.getSubjectId())) {
            throw new BusinessException(ErrorCode.GOAL_SUBJECT_MISMATCH);
        }
    }

    private void applyEditableFields(StudyTask task, long subjectId, Long goalId, String title, String description,
                                     int priority, int estimatedMinutes, LocalDate plannedDate, LocalDateTime dueAt) {
        task.setSubjectId(subjectId);
        task.setGoalId(goalId);
        task.setTitle(title.trim());
        task.setDescription(trimToNull(description));
        task.setPriority(priority);
        task.setEstimatedMinutes(estimatedMinutes);
        task.setPlannedDate(plannedDate);
        task.setDueAt(dueAt);
    }

    private void updateOwned(StudyTask task, long userId) {
        int updated = taskMapper.update(task, new LambdaUpdateWrapper<StudyTask>()
                .eq(StudyTask::getId, task.getId()).eq(StudyTask::getUserId, userId));
        if (updated != 1) throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

