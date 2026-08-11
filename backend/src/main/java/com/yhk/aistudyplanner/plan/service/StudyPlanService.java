package com.yhk.aistudyplanner.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.plan.dto.PlanConfirmItemRequest;
import com.yhk.aistudyplanner.plan.dto.PlanConfirmRequest;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.dto.PlanItemCompleteRequest;
import com.yhk.aistudyplanner.plan.dto.PlanItemStatusRequest;
import com.yhk.aistudyplanner.plan.dto.PlanStatusRequest;
import com.yhk.aistudyplanner.plan.entity.PlanItemStatus;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import com.yhk.aistudyplanner.plan.entity.StudyPlan;
import com.yhk.aistudyplanner.plan.entity.StudyPlanItem;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanItemMapper;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanMapper;
import com.yhk.aistudyplanner.plan.vo.PlanDetailView;
import com.yhk.aistudyplanner.plan.vo.PlanDraftView;
import com.yhk.aistudyplanner.plan.vo.PlanItemView;
import com.yhk.aistudyplanner.plan.vo.PlanListView;
import com.yhk.aistudyplanner.record.entity.StudyRecord;
import com.yhk.aistudyplanner.record.mapper.StudyRecordMapper;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPlanService {
    private static final int MAX_ACTUAL_MINUTES = 720;

    private final StudyPlanMapper planMapper;
    private final StudyPlanItemMapper itemMapper;
    private final StudyRecordMapper recordMapper;
    private final TaskMapper taskMapper;
    private final AuthSessionService session;
    private final RuleBasedPlanGenerator generator;
    private final Clock clock;

    public StudyPlanService(
            StudyPlanMapper planMapper,
            StudyPlanItemMapper itemMapper,
            StudyRecordMapper recordMapper,
            TaskMapper taskMapper,
            AuthSessionService session,
            RuleBasedPlanGenerator generator,
            Clock clock) {
        this.planMapper = planMapper;
        this.itemMapper = itemMapper;
        this.recordMapper = recordMapper;
        this.taskMapper = taskMapper;
        this.session = session;
        this.generator = generator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlanDraftView draft(PlanDraftRequest request) {
        return generator.generate(session.currentUserId(), request);
    }

    @Transactional
    public PlanDetailView confirm(PlanConfirmRequest request) {
        long userId = session.currentUserId();
        StudyPlan existing = findByDraft(userId, request.draftId());
        if (existing != null) return detail(existing, userId);
        validateConfirm(userId, request);
        LocalDateTime now = LocalDateTime.now(clock);
        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setSourceDraftId(request.draftId());
        plan.setPlanDate(request.planDate());
        plan.setAvailableMinutes(request.availableMinutes());
        plan.setPlannedMinutes(request.plannedMinutes());
        plan.setRequirement(trimToNull(request.requirement()));
        plan.setSummary(request.summary().trim());
        plan.setStatus(PlanStatus.CONFIRMED);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        try {
            planMapper.insert(plan);
        } catch (DuplicateKeyException exception) {
            StudyPlan duplicate = findByDraft(userId, request.draftId());
            if (duplicate != null) return detail(duplicate, userId);
            throw exception;
        }
        for (PlanConfirmItemRequest source :
                request.items().stream()
                        .sorted(Comparator.comparingInt(PlanConfirmItemRequest::sequenceNo))
                        .toList()) {
            StudyPlanItem item = new StudyPlanItem();
            item.setUserId(userId);
            item.setPlanId(plan.getId());
            item.setTaskId(source.taskId());
            item.setSequenceNo(source.sequenceNo());
            item.setStartAt(source.startAt());
            item.setEndAt(source.endAt());
            item.setPlannedMinutes(source.plannedMinutes());
            item.setReason(source.reason().trim());
            item.setStatus(PlanItemStatus.PENDING);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            itemMapper.insert(item);
        }
        return detail(plan, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlanListView> list(
            long page,
            long pageSize,
            LocalDate start,
            LocalDate end,
            PlanStatus status) {
        if (start != null && end != null) {
            if (start.isAfter(end)) throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
            if (ChronoUnit.DAYS.between(start, end) + 1 > 366) {
                throw new BusinessException(ErrorCode.DATE_RANGE_TOO_LARGE);
            }
        }
        long userId = session.currentUserId();
        var result =
                planMapper.selectPlanPage(new Page<>(page, pageSize), userId, start, end, status);
        return new PageResponse<>(
                result.getRecords(), page, pageSize, result.getTotal());
    }

    @Transactional(readOnly = true)
    public PlanDetailView get(long id) {
        long userId = session.currentUserId();
        return detail(requireOwned(id, userId), userId);
    }

    @Transactional
    public PlanDetailView changeStatus(long id, PlanStatusRequest request) {
        long userId = session.currentUserId();
        StudyPlan plan = lockOwnedPlan(id, userId);
        if (request.status() != PlanStatus.CANCELLED || plan.getStatus() != PlanStatus.CONFIRMED) {
            throw new BusinessException(
                    plan.getStatus() == PlanStatus.CANCELLED
                            ? ErrorCode.PLAN_ALREADY_CANCELLED
                            : ErrorCode.INVALID_PLAN_STATUS_TRANSITION);
        }
        updatePlanStatus(id, userId, PlanStatus.CANCELLED);
        plan.setStatus(PlanStatus.CANCELLED);
        plan.setUpdatedAt(LocalDateTime.now(clock));
        return detail(plan, userId);
    }

    @Transactional
    public PlanDetailView completeItem(
            long planId, long itemId, PlanItemCompleteRequest request) {
        long userId = session.currentUserId();
        StudyPlan plan = lockOwnedPlan(planId, userId);
        ensurePlanExecutable(plan);
        StudyPlanItem item = lockOwnedItem(planId, itemId, userId);
        if (item.getStatus() == PlanItemStatus.COMPLETED) {
            return detail(plan, userId);
        }
        if (item.getStatus() != PlanItemStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_PLAN_ITEM_STATUS_TRANSITION);
        }
        StudyTask task = taskMapper.selectOwnedForUpdate(item.getTaskId(), userId);
        if (task == null) throw new BusinessException(ErrorCode.PLAN_TASK_INVALID);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime recordStartedAt = request.actualStartAt();
        LocalDateTime recordEndedAt = request.actualEndAt();
        int actualMinutes = validateAndCalculateActualMinutes(recordStartedAt, recordEndedAt, now);
        if (recordMapper.countOverlapping(userId, recordStartedAt, recordEndedAt, null) > 0) {
            throw new BusinessException(ErrorCode.RECORD_TIME_OVERLAP);
        }

        TaskStatus previousTaskStatus = null;
        LocalDateTime taskCompletedAt = null;
        if (Boolean.TRUE.equals(request.completeTask())) {
            if (task.getStatus() == TaskStatus.CANCELLED) {
                throw new BusinessException(ErrorCode.INVALID_TASK_STATUS);
            }
            if (task.getStatus() == TaskStatus.TODO || task.getStatus() == TaskStatus.IN_PROGRESS) {
                previousTaskStatus = task.getStatus();
                taskCompletedAt = now;
                completeTask(task, userId, previousTaskStatus, taskCompletedAt);
            }
        }

        StudyRecord existingRecord = recordMapper.selectByPlanItem(userId, planId, itemId);
        if (existingRecord == null) {
            insertExecutionRecord(
                    userId,
                    planId,
                    item,
                    task,
                    recordStartedAt,
                    recordEndedAt,
                    actualMinutes,
                    request.feedback(),
                    now);
        }

        int updated =
                itemMapper.update(
                        null,
                        new LambdaUpdateWrapper<StudyPlanItem>()
                                .eq(StudyPlanItem::getId, itemId)
                                .eq(StudyPlanItem::getPlanId, planId)
                                .eq(StudyPlanItem::getUserId, userId)
                                .eq(StudyPlanItem::getStatus, PlanItemStatus.PENDING)
                                .set(StudyPlanItem::getStatus, PlanItemStatus.COMPLETED)
                                .set(
                                        StudyPlanItem::getTaskStatusBeforeCompletion,
                                        previousTaskStatus)
                                .set(StudyPlanItem::getTaskCompletedAt, taskCompletedAt)
                                .set(StudyPlanItem::getUpdatedAt, now));
        if (updated != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        recalculatePlanStatus(plan, userId);
        return detail(plan, userId);
    }

    @Transactional
    public PlanDetailView changeItemStatus(
            long planId, long itemId, PlanItemStatusRequest request) {
        if (request.status() == PlanItemStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_PLAN_ITEM_STATUS_TRANSITION);
        }
        long userId = session.currentUserId();
        StudyPlan plan = lockOwnedPlan(planId, userId);
        ensurePlanExecutable(plan);
        StudyPlanItem item = lockOwnedItem(planId, itemId, userId);
        if (item.getStatus() == request.status()) return detail(plan, userId);
        if (!allowed(item.getStatus(), request.status())) {
            throw new BusinessException(ErrorCode.INVALID_PLAN_ITEM_STATUS_TRANSITION);
        }
        if (request.status() == PlanItemStatus.PENDING
                && item.getStatus() == PlanItemStatus.COMPLETED) {
            restoreCompletedItem(plan, item, userId);
        } else {
            updateSimpleItemStatus(planId, itemId, userId, request.status());
        }
        recalculatePlanStatus(plan, userId);
        return detail(plan, userId);
    }

    private void insertExecutionRecord(
            long userId,
            long planId,
            StudyPlanItem item,
            StudyTask task,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            int actualMinutes,
            String feedback,
            LocalDateTime now) {
        StudyRecord record = new StudyRecord();
        record.setUserId(userId);
        record.setSubjectId(task.getSubjectId());
        record.setTaskId(task.getId());
        record.setPlanId(planId);
        record.setPlanItemId(item.getId());
        record.setStartedAt(startedAt);
        record.setEndedAt(endedAt);
        record.setDurationMinutes(actualMinutes);
        record.setFeedback(trimToNull(feedback));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            if (recordMapper.selectByPlanItem(userId, planId, item.getId()) == null) {
                throw exception;
            }
        }
    }

    private void completeTask(
            StudyTask task,
            long userId,
            TaskStatus expectedStatus,
            LocalDateTime completedAt) {
        int updated =
                taskMapper.update(
                        null,
                        new LambdaUpdateWrapper<StudyTask>()
                                .eq(StudyTask::getId, task.getId())
                                .eq(StudyTask::getUserId, userId)
                                .eq(StudyTask::getStatus, expectedStatus)
                                .set(StudyTask::getStatus, TaskStatus.COMPLETED)
                                .set(StudyTask::getCompletedAt, completedAt)
                                .set(StudyTask::getUpdatedAt, completedAt));
        if (updated != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private void restoreCompletedItem(StudyPlan plan, StudyPlanItem item, long userId) {
        if (item.getTaskStatusBeforeCompletion() != null
                && item.getTaskCompletedAt() != null) {
            StudyTask task = taskMapper.selectOwnedForUpdate(item.getTaskId(), userId);
            if (task == null) throw new BusinessException(ErrorCode.PLAN_TASK_INVALID);
            if (task.getStatus() == TaskStatus.COMPLETED
                    && item.getTaskCompletedAt().equals(task.getCompletedAt())) {
                int taskUpdated =
                        taskMapper.update(
                                null,
                                new LambdaUpdateWrapper<StudyTask>()
                                        .eq(StudyTask::getId, task.getId())
                                        .eq(StudyTask::getUserId, userId)
                                        .eq(StudyTask::getStatus, TaskStatus.COMPLETED)
                                        .eq(
                                                StudyTask::getCompletedAt,
                                                item.getTaskCompletedAt())
                                        .set(
                                                StudyTask::getStatus,
                                                item.getTaskStatusBeforeCompletion())
                                        .set(StudyTask::getCompletedAt, null)
                                        .set(
                                                StudyTask::getUpdatedAt,
                                                LocalDateTime.now(clock)));
                if (taskUpdated != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
            }
        }

        StudyRecord record =
                recordMapper.selectByPlanItem(userId, plan.getId(), item.getId());
        if (record != null) {
            int deleted =
                    recordMapper.delete(
                            new LambdaQueryWrapper<StudyRecord>()
                                    .eq(StudyRecord::getId, record.getId())
                                    .eq(StudyRecord::getUserId, userId)
                                    .eq(StudyRecord::getPlanId, plan.getId())
                                    .eq(StudyRecord::getPlanItemId, item.getId()));
            if (deleted != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }

        int itemUpdated =
                itemMapper.update(
                        null,
                        new LambdaUpdateWrapper<StudyPlanItem>()
                                .eq(StudyPlanItem::getId, item.getId())
                                .eq(StudyPlanItem::getPlanId, plan.getId())
                                .eq(StudyPlanItem::getUserId, userId)
                                .eq(StudyPlanItem::getStatus, PlanItemStatus.COMPLETED)
                                .set(StudyPlanItem::getStatus, PlanItemStatus.PENDING)
                                .set(StudyPlanItem::getTaskStatusBeforeCompletion, null)
                                .set(StudyPlanItem::getTaskCompletedAt, null)
                                .set(StudyPlanItem::getUpdatedAt, LocalDateTime.now(clock)));
        if (itemUpdated != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private void updateSimpleItemStatus(
            long planId, long itemId, long userId, PlanItemStatus status) {
        int updated =
                itemMapper.update(
                        null,
                        new LambdaUpdateWrapper<StudyPlanItem>()
                                .eq(StudyPlanItem::getId, itemId)
                                .eq(StudyPlanItem::getPlanId, planId)
                                .eq(StudyPlanItem::getUserId, userId)
                                .set(StudyPlanItem::getStatus, status)
                                .set(StudyPlanItem::getUpdatedAt, LocalDateTime.now(clock)));
        if (updated != 1) throw new BusinessException(ErrorCode.PLAN_ITEM_NOT_FOUND);
    }

    private void recalculatePlanStatus(StudyPlan plan, long userId) {
        List<StudyPlanItem> items =
                itemMapper.selectList(
                        new LambdaQueryWrapper<StudyPlanItem>()
                                .eq(StudyPlanItem::getPlanId, plan.getId())
                                .eq(StudyPlanItem::getUserId, userId));
        long completed =
                items.stream()
                        .filter(item -> item.getStatus() == PlanItemStatus.COMPLETED)
                        .count();
        long skipped =
                items.stream()
                        .filter(item -> item.getStatus() == PlanItemStatus.SKIPPED)
                        .count();
        long pending =
                items.stream()
                        .filter(item -> item.getStatus() == PlanItemStatus.PENDING)
                        .count();
        PlanStatus next;
        if (pending > 0) {
            next = PlanStatus.CONFIRMED;
        } else if (completed == items.size()) {
            next = PlanStatus.COMPLETED;
        } else if (skipped == items.size()) {
            next = PlanStatus.ABANDONED;
        } else {
            next = PlanStatus.PARTIALLY_COMPLETED;
        }
        updatePlanStatus(plan.getId(), userId, next);
        plan.setStatus(next);
        plan.setUpdatedAt(LocalDateTime.now(clock));
    }

    private boolean allowed(PlanItemStatus from, PlanItemStatus to) {
        return from == PlanItemStatus.PENDING && to == PlanItemStatus.SKIPPED
                || (from == PlanItemStatus.COMPLETED || from == PlanItemStatus.SKIPPED)
                        && to == PlanItemStatus.PENDING;
    }

    private void ensurePlanExecutable(StudyPlan plan) {
        if (plan.getStatus() == PlanStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_CANCELLED);
        }
    }

    private int validateAndCalculateActualMinutes(
            LocalDateTime startedAt, LocalDateTime endedAt, LocalDateTime now) {
        if (!endedAt.isAfter(startedAt)) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_TIME);
        }
        if (endedAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.RECORD_END_TIME_IN_FUTURE);
        }
        if (!startedAt.toLocalDate().equals(endedAt.toLocalDate())) {
            throw new BusinessException(ErrorCode.RECORD_CROSSES_DAY);
        }
        if (endedAt.isAfter(startedAt.plusMinutes(MAX_ACTUAL_MINUTES))) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_DURATION);
        }
        long actualMinutes = ChronoUnit.MINUTES.between(startedAt, endedAt);
        if (actualMinutes < 1 || actualMinutes > MAX_ACTUAL_MINUTES) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_DURATION);
        }
        return Math.toIntExact(actualMinutes);
    }

    private void validateConfirm(long userId, PlanConfirmRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.PLAN_DRAFT_EMPTY);
        }
        if (request.planDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.PLAN_DATE_IN_PAST);
        }
        List<PlanConfirmItemRequest> items =
                request.items().stream()
                        .sorted(Comparator.comparingInt(PlanConfirmItemRequest::sequenceNo))
                        .toList();
        Set<Long> ids = new HashSet<>();
        int total = 0;
        LocalDateTime previous = null;
        for (int index = 0; index < items.size(); index++) {
            PlanConfirmItemRequest item = items.get(index);
            if (item.sequenceNo() != index + 1) {
                throw new BusinessException(ErrorCode.INVALID_PLAN_TIME);
            }
            if (!ids.add(item.taskId())) {
                throw new BusinessException(ErrorCode.PLAN_TASK_DUPLICATED);
            }
            if (!item.startAt().toLocalDate().equals(request.planDate())
                    || !item.endAt().toLocalDate().equals(request.planDate())
                    || !item.endAt().isAfter(item.startAt())) {
                throw new BusinessException(ErrorCode.INVALID_PLAN_TIME);
            }
            long minutes = ChronoUnit.MINUTES.between(item.startAt(), item.endAt());
            if (minutes != item.plannedMinutes() || minutes < 15) {
                throw new BusinessException(ErrorCode.INVALID_PLAN_DURATION);
            }
            if (previous != null && item.startAt().isBefore(previous)) {
                throw new BusinessException(ErrorCode.PLAN_TIME_OVERLAP);
            }
            previous = item.endAt();
            total += item.plannedMinutes();
        }
        if (total != request.plannedMinutes() || total > request.availableMinutes()) {
            throw new BusinessException(ErrorCode.INVALID_PLAN_DURATION);
        }
        List<StudyTask> tasks =
                taskMapper.selectList(
                        new LambdaQueryWrapper<StudyTask>()
                                .eq(StudyTask::getUserId, userId)
                                .in(StudyTask::getId, ids)
                                .in(
                                        StudyTask::getStatus,
                                        TaskStatus.TODO,
                                        TaskStatus.IN_PROGRESS));
        if (tasks.size() != ids.size()) {
            throw new BusinessException(ErrorCode.PLAN_TASK_INVALID);
        }
    }

    private StudyPlan findByDraft(long userId, String draft) {
        return planMapper.selectOne(
                new LambdaQueryWrapper<StudyPlan>()
                        .eq(StudyPlan::getUserId, userId)
                        .eq(StudyPlan::getSourceDraftId, draft));
    }

    private StudyPlan requireOwned(long id, long userId) {
        StudyPlan plan =
                planMapper.selectOne(
                        new LambdaQueryWrapper<StudyPlan>()
                                .eq(StudyPlan::getId, id)
                                .eq(StudyPlan::getUserId, userId));
        if (plan != null) return plan;
        if (planMapper.selectById(id) != null) {
            throw new BusinessException(ErrorCode.PLAN_ACCESS_DENIED);
        }
        throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
    }

    private StudyPlan lockOwnedPlan(long id, long userId) {
        StudyPlan plan = planMapper.selectOwnedForUpdate(id, userId);
        if (plan != null) return plan;
        if (planMapper.selectById(id) != null) {
            throw new BusinessException(ErrorCode.PLAN_ACCESS_DENIED);
        }
        throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
    }

    private StudyPlanItem lockOwnedItem(long planId, long itemId, long userId) {
        StudyPlanItem item = itemMapper.selectOwnedForUpdate(planId, itemId, userId);
        if (item == null) throw new BusinessException(ErrorCode.PLAN_ITEM_NOT_FOUND);
        return item;
    }

    private void updatePlanStatus(long id, long userId, PlanStatus status) {
        int updated =
                planMapper.update(
                        null,
                        new LambdaUpdateWrapper<StudyPlan>()
                                .eq(StudyPlan::getId, id)
                                .eq(StudyPlan::getUserId, userId)
                                .set(StudyPlan::getStatus, status)
                                .set(StudyPlan::getUpdatedAt, LocalDateTime.now(clock)));
        if (updated != 1) throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
    }

    private PlanDetailView detail(StudyPlan plan, long userId) {
        List<PlanItemView> items = itemMapper.selectViews(plan.getId(), userId);
        long completed =
                items.stream().filter(item -> item.status() == PlanItemStatus.COMPLETED).count();
        long skipped =
                items.stream().filter(item -> item.status() == PlanItemStatus.SKIPPED).count();
        long pending =
                items.stream().filter(item -> item.status() == PlanItemStatus.PENDING).count();
        int actualMinutes =
                items.stream()
                        .map(PlanItemView::actualMinutes)
                        .filter(java.util.Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum();
        double completionPercentage =
                items.isEmpty()
                        ? 0
                        : Math.round(completed * 10000.0 / items.size()) / 100.0;
        return new PlanDetailView(
                plan.getId(),
                plan.getSourceDraftId(),
                plan.getPlanDate(),
                plan.getAvailableMinutes(),
                plan.getPlannedMinutes(),
                plan.getRequirement(),
                plan.getSummary(),
                plan.getStatus(),
                completed,
                skipped,
                pending,
                actualMinutes,
                completionPercentage,
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                items);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
