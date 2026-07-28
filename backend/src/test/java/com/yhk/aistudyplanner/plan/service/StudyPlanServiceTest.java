package com.yhk.aistudyplanner.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.plan.dto.PlanConfirmItemRequest;
import com.yhk.aistudyplanner.plan.dto.PlanConfirmRequest;
import com.yhk.aistudyplanner.plan.dto.PlanItemCompleteRequest;
import com.yhk.aistudyplanner.plan.dto.PlanItemStatusRequest;
import com.yhk.aistudyplanner.plan.dto.PlanStatusRequest;
import com.yhk.aistudyplanner.plan.entity.PlanItemStatus;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import com.yhk.aistudyplanner.plan.entity.StudyPlan;
import com.yhk.aistudyplanner.plan.entity.StudyPlanItem;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanItemMapper;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanMapper;
import com.yhk.aistudyplanner.plan.vo.PlanItemView;
import com.yhk.aistudyplanner.record.entity.StudyRecord;
import com.yhk.aistudyplanner.record.mapper.StudyRecordMapper;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class StudyPlanServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 23);
    private static final LocalDateTime NOW = DATE.atTime(20, 0);

    @Mock StudyPlanMapper planMapper;
    @Mock StudyPlanItemMapper itemMapper;
    @Mock StudyRecordMapper recordMapper;
    @Mock TaskMapper taskMapper;
    @Mock AuthSessionService session;
    @Mock RuleBasedPlanGenerator generator;

    private StudyPlanService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "plan-service-test");
        TableInfoHelper.initTableInfo(assistant, StudyPlan.class);
        TableInfoHelper.initTableInfo(assistant, StudyPlanItem.class);
        TableInfoHelper.initTableInfo(assistant, StudyTask.class);
        TableInfoHelper.initTableInfo(assistant, StudyRecord.class);
        Clock clock =
                Clock.fixed(
                        NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                        ZoneId.of("Asia/Shanghai"));
        service =
                new StudyPlanService(
                        planMapper,
                        itemMapper,
                        recordMapper,
                        taskMapper,
                        session,
                        generator,
                        clock);
        lenient().when(session.currentUserId()).thenReturn(1L);
    }

    @Test
    void confirmsPlanInOneLogicalOperation() {
        when(planMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.selectList(any())).thenReturn(List.of(task(10L), task(11L)));
        when(planMapper.insert(any(StudyPlan.class)))
                .thenAnswer(
                        invocation -> {
                            ((StudyPlan) invocation.getArgument(0)).setId(7L);
                            return 1;
                        });
        when(itemMapper.selectViews(7L, 1L)).thenReturn(List.of());

        var result =
                service.confirm(
                        request(
                                "draft-1",
                                List.of(itemRequest(1, 10, 9, 60), itemRequest(2, 11, 10, 30)),
                                90));

        assertEquals(PlanStatus.CONFIRMED, result.status());
        verify(itemMapper, times(2)).insert(any(StudyPlanItem.class));
    }

    @Test
    void repeatedDraftReturnsExistingWithoutInsert() {
        StudyPlan existing = plan(7L, PlanStatus.CONFIRMED);
        when(planMapper.selectOne(any())).thenReturn(existing);
        when(itemMapper.selectViews(7L, 1L)).thenReturn(List.of());

        assertEquals(
                7L,
                service.confirm(
                                request(
                                        "same",
                                        List.of(itemRequest(1, 10, 9, 60)),
                                        60))
                        .id());
        verify(planMapper, never()).insert(any(StudyPlan.class));
    }

    @Test
    void completesItemAndCreatesExecutionRecordWithActualMinutes() {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        StudyPlanItem item = planItem(2L, PlanItemStatus.PENDING);
        StudyTask task = task(10L);
        prepareCompletion(plan, item, task, List.of(completedItemView(2L, 45, "完成练习")));

        var result =
                service.completeItem(
                        7L, 2L, completion(45, " 完成练习 ", false));

        ArgumentCaptor<StudyRecord> recordCaptor = ArgumentCaptor.forClass(StudyRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        StudyRecord record = recordCaptor.getValue();
        assertEquals(1L, record.getUserId());
        assertEquals(7L, record.getPlanId());
        assertEquals(2L, record.getPlanItemId());
        assertEquals(10L, record.getTaskId());
        assertEquals(45, record.getDurationMinutes());
        assertEquals(DATE.atTime(9, 0), record.getStartedAt());
        assertEquals(DATE.atTime(9, 45), record.getEndedAt());
        assertEquals("完成练习", record.getFeedback());
        assertEquals(45, result.actualStudyMinutes());
        assertEquals(1L, result.completedItemCount());
        verify(taskMapper, never()).update(isNull(), any());
    }

    @Test
    void futurePlannedItemCanBeCompletedWithActualPastTime() {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        StudyPlanItem item = planItem(2L, PlanItemStatus.PENDING);
        item.setStartAt(DATE.plusDays(1).atTime(9, 0));
        item.setEndAt(DATE.plusDays(1).atTime(10, 0));
        StudyTask task = task(10L);
        prepareCompletion(plan, item, task, List.of(completedItemView(2L, 30, null)));

        service.completeItem(
                7L,
                2L,
                new PlanItemCompleteRequest(
                        DATE.atTime(19, 15), DATE.atTime(19, 45), null, false));

        ArgumentCaptor<StudyRecord> recordCaptor = ArgumentCaptor.forClass(StudyRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals(DATE.atTime(19, 15), recordCaptor.getValue().getStartedAt());
        assertEquals(DATE.atTime(19, 45), recordCaptor.getValue().getEndedAt());
        assertEquals(30, recordCaptor.getValue().getDurationMinutes());
    }

    @Test
    void actualDurationIsCalculatedFromTimeRange() {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        StudyPlanItem item = planItem(2L, PlanItemStatus.PENDING);
        StudyTask task = task(10L);
        prepareCompletion(plan, item, task, List.of(completedItemView(2L, 35, null)));

        service.completeItem(
                7L,
                2L,
                new PlanItemCompleteRequest(
                        DATE.atTime(18, 25), DATE.atTime(19, 0), null, false));

        ArgumentCaptor<StudyRecord> recordCaptor = ArgumentCaptor.forClass(StudyRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals(35, recordCaptor.getValue().getDurationMinutes());
    }

    @Test
    void invalidActualTimeRangeIsRejected() {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        StudyPlanItem item = planItem(2L, PlanItemStatus.PENDING);
        StudyTask task = task(10L);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(7L, 2L, 1L)).thenReturn(item);
        when(taskMapper.selectOwnedForUpdate(10L, 1L)).thenReturn(task);

        BusinessException reversed =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.completeItem(
                                        7L,
                                        2L,
                                        new PlanItemCompleteRequest(
                                                DATE.atTime(19, 30),
                                                DATE.atTime(19, 0),
                                                null,
                                                false)));
        assertEquals(ErrorCode.INVALID_RECORD_TIME, reversed.getErrorCode());

        BusinessException future =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.completeItem(
                                        7L,
                                        2L,
                                        new PlanItemCompleteRequest(
                                                DATE.atTime(19, 30),
                                                NOW.plusMinutes(1),
                                                null,
                                                false)));
        assertEquals(ErrorCode.RECORD_END_TIME_IN_FUTURE, future.getErrorCode());
        verify(recordMapper, never()).insert(any(StudyRecord.class));
    }

    @Test
    void optionallyCompletesOriginalTask() {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        StudyPlanItem item = planItem(2L, PlanItemStatus.PENDING);
        StudyTask task = task(10L);
        prepareCompletion(plan, item, task, List.of(completedItemView(2L, 60, null)));
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        service.completeItem(7L, 2L, completion(60, null, true));

        verify(taskMapper).update(isNull(), any());
        verify(recordMapper).insert(any(StudyRecord.class));
    }

    @Test
    void repeatedCompletionIsIdempotent() {
        StudyPlan plan = plan(7L, PlanStatus.COMPLETED);
        StudyPlanItem item = planItem(2L, PlanItemStatus.COMPLETED);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(7L, 2L, 1L)).thenReturn(item);
        when(itemMapper.selectViews(7L, 1L))
                .thenReturn(List.of(completedItemView(2L, 60, null)));

        var result =
                service.completeItem(
                        7L, 2L, completion(60, "重复点击", true));

        assertEquals(PlanStatus.COMPLETED, result.status());
        verify(recordMapper, never()).insert(any(StudyRecord.class));
        verify(taskMapper, never()).update(isNull(), any());
        verify(itemMapper, never()).update(isNull(), any());
    }

    @Test
    void nonOwnerAndCancelledPlanCannotCompleteItem() {
        StudyPlan other = plan(7L, PlanStatus.CONFIRMED);
        other.setUserId(2L);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(null);
        when(planMapper.selectById(7L)).thenReturn(other);
        BusinessException denied =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.completeItem(
                                        7L,
                                        2L,
                                        completion(60, null, false)));
        assertEquals(ErrorCode.PLAN_ACCESS_DENIED, denied.getErrorCode());

        StudyPlan cancelled = plan(8L, PlanStatus.CANCELLED);
        when(planMapper.selectOwnedForUpdate(8L, 1L)).thenReturn(cancelled);
        BusinessException blocked =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.completeItem(
                                        8L,
                                        2L,
                                        completion(60, null, false)));
        assertEquals(ErrorCode.PLAN_ALREADY_CANCELLED, blocked.getErrorCode());
    }

    @Test
    void skippingItemDoesNotCreateRecordOrCompleteTask() {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        StudyPlanItem skipped = planItem(2L, PlanItemStatus.PENDING);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(7L, 2L, 1L)).thenReturn(skipped);
        when(itemMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of(planItem(2L, PlanItemStatus.SKIPPED)));
        when(planMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectViews(7L, 1L)).thenReturn(List.of(skippedItemView(2L)));

        var result =
                service.changeItemStatus(
                        7L, 2L, new PlanItemStatusRequest(PlanItemStatus.SKIPPED));

        assertEquals(PlanStatus.ABANDONED, result.status());
        verify(recordMapper, never()).insert(any(StudyRecord.class));
        verify(taskMapper, never()).update(isNull(), any());
    }

    @Test
    void recalculatesCompletedPartialAndAbandonedStatuses() {
        assertEquals(
                PlanStatus.COMPLETED,
                changePendingItemWithResult(
                                PlanItemStatus.SKIPPED,
                                List.of(planItem(1L, PlanItemStatus.COMPLETED)),
                                List.of(completedItemView(1L, 60, null)))
                        .status());

        assertEquals(
                PlanStatus.PARTIALLY_COMPLETED,
                changePendingItemWithResult(
                                PlanItemStatus.SKIPPED,
                                List.of(
                                        planItem(1L, PlanItemStatus.COMPLETED),
                                        planItem(2L, PlanItemStatus.SKIPPED)),
                                List.of(
                                        completedItemView(1L, 60, null),
                                        skippedItemView(2L)))
                        .status());

        assertEquals(
                PlanStatus.ABANDONED,
                changePendingItemWithResult(
                                PlanItemStatus.SKIPPED,
                                List.of(
                                        planItem(1L, PlanItemStatus.SKIPPED),
                                        planItem(2L, PlanItemStatus.SKIPPED)),
                                List.of(skippedItemView(1L), skippedItemView(2L)))
                        .status());
    }

    @Test
    void completedRestoreDeletesOnlyLinkedRecordAndRestoresTaskWhenStillOwnedByOperation() {
        StudyPlan plan = plan(7L, PlanStatus.COMPLETED);
        StudyPlanItem completed = planItem(2L, PlanItemStatus.COMPLETED);
        completed.setActualMinutes(60);
        completed.setFeedback("反馈");
        completed.setTaskStatusBeforeCompletion(TaskStatus.IN_PROGRESS);
        completed.setTaskCompletedAt(NOW);
        StudyTask task = task(10L);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(NOW);
        StudyRecord record = new StudyRecord();
        record.setId(99L);
        record.setUserId(1L);
        record.setPlanId(7L);
        record.setPlanItemId(2L);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(7L, 2L, 1L)).thenReturn(completed);
        when(taskMapper.selectOwnedForUpdate(10L, 1L)).thenReturn(task);
        when(taskMapper.update(isNull(), any())).thenReturn(1);
        when(recordMapper.selectByPlanItem(1L, 7L, 2L)).thenReturn(record);
        when(recordMapper.delete(any())).thenReturn(1);
        when(itemMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of(planItem(2L, PlanItemStatus.PENDING)));
        when(planMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectViews(7L, 1L)).thenReturn(List.of(pendingItemView(2L)));

        var result =
                service.changeItemStatus(
                        7L, 2L, new PlanItemStatusRequest(PlanItemStatus.PENDING));

        assertEquals(PlanStatus.CONFIRMED, result.status());
        verify(recordMapper).delete(any());
        verify(taskMapper).update(isNull(), any());
    }

    @Test
    void completedRestoreDoesNotTouchTaskThatWasNotCompletedByThisOperation() {
        StudyPlan plan = plan(7L, PlanStatus.COMPLETED);
        StudyPlanItem completed = planItem(2L, PlanItemStatus.COMPLETED);
        StudyRecord record = new StudyRecord();
        record.setId(99L);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(7L, 2L, 1L)).thenReturn(completed);
        when(recordMapper.selectByPlanItem(1L, 7L, 2L)).thenReturn(record);
        when(recordMapper.delete(any())).thenReturn(1);
        when(itemMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of(planItem(2L, PlanItemStatus.PENDING)));
        when(planMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectViews(7L, 1L)).thenReturn(List.of(pendingItemView(2L)));

        service.changeItemStatus(
                7L, 2L, new PlanItemStatusRequest(PlanItemStatus.PENDING));

        verify(taskMapper, never()).selectOwnedForUpdate(anyLong(), anyLong());
        verify(taskMapper, never()).update(isNull(), any());
    }

    @Test
    void skippedRestoreOnlyChangesItemStatus() {
        StudyPlan plan = plan(7L, PlanStatus.ABANDONED);
        StudyPlanItem skipped = planItem(2L, PlanItemStatus.SKIPPED);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(7L, 2L, 1L)).thenReturn(skipped);
        when(itemMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of(planItem(2L, PlanItemStatus.PENDING)));
        when(planMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectViews(7L, 1L)).thenReturn(List.of(pendingItemView(2L)));

        var result =
                service.changeItemStatus(
                        7L, 2L, new PlanItemStatusRequest(PlanItemStatus.PENDING));

        assertEquals(PlanStatus.CONFIRMED, result.status());
        verify(recordMapper, never()).delete(any());
        verify(taskMapper, never()).update(isNull(), any());
    }

    @Test
    void recordFailureAndTaskFailureAbortCompletionTransaction() throws Exception {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        StudyPlanItem item = planItem(2L, PlanItemStatus.PENDING);
        StudyTask task = task(10L);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(7L, 2L, 1L)).thenReturn(item);
        when(taskMapper.selectOwnedForUpdate(10L, 1L)).thenReturn(task);
        when(recordMapper.countOverlapping(anyLong(), any(), any(), isNull()))
                .thenReturn(0L);
        when(recordMapper.selectByPlanItem(1L, 7L, 2L)).thenReturn(null);
        when(recordMapper.insert(any(StudyRecord.class)))
                .thenThrow(new IllegalStateException("record insert failed"));

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.completeItem(
                                7L,
                                2L,
                                completion(60, null, false)));
        verify(itemMapper, never()).update(isNull(), any());

        when(taskMapper.update(isNull(), any())).thenReturn(0);
        assertThrows(
                BusinessException.class,
                () ->
                        service.completeItem(
                                7L,
                                2L,
                                completion(60, null, true)));
        verify(recordMapper, times(1)).insert(any(StudyRecord.class));
        assertTrue(
                StudyPlanService.class
                                .getMethod(
                                        "completeItem",
                                        long.class,
                                        long.class,
                                        PlanItemCompleteRequest.class)
                                .getAnnotation(Transactional.class)
                        != null);
    }

    @Test
    void cancelAndDirectCompletedStatusRulesRemainSafe() {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(planMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectViews(7L, 1L)).thenReturn(List.of());
        assertEquals(
                PlanStatus.CANCELLED,
                service.changeStatus(7L, new PlanStatusRequest(PlanStatus.CANCELLED)).status());
        assertEquals(
                ErrorCode.INVALID_PLAN_ITEM_STATUS_TRANSITION,
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.changeItemStatus(
                                                7L,
                                                2L,
                                                new PlanItemStatusRequest(
                                                        PlanItemStatus.COMPLETED)))
                        .getErrorCode());
    }

    private void prepareCompletion(
            StudyPlan plan,
            StudyPlanItem item,
            StudyTask task,
            List<PlanItemView> detailViews) {
        when(planMapper.selectOwnedForUpdate(plan.getId(), 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(plan.getId(), item.getId(), 1L)).thenReturn(item);
        when(taskMapper.selectOwnedForUpdate(task.getId(), 1L)).thenReturn(task);
        when(recordMapper.countOverlapping(anyLong(), any(), any(), isNull()))
                .thenReturn(0L);
        when(recordMapper.selectByPlanItem(1L, plan.getId(), item.getId())).thenReturn(null);
        when(recordMapper.insert(any(StudyRecord.class))).thenReturn(1);
        when(itemMapper.update(isNull(), any())).thenReturn(1);
        StudyPlanItem completed = planItem(item.getId(), PlanItemStatus.COMPLETED);
        completed.setActualMinutes(detailViews.get(0).actualMinutes());
        when(itemMapper.selectList(any())).thenReturn(List.of(completed));
        when(planMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectViews(plan.getId(), 1L)).thenReturn(detailViews);
    }

    private com.yhk.aistudyplanner.plan.vo.PlanDetailView changePendingItemWithResult(
            PlanItemStatus target,
            List<StudyPlanItem> resultingItems,
            List<PlanItemView> views) {
        StudyPlan plan = plan(7L, PlanStatus.CONFIRMED);
        StudyPlanItem current = planItem(99L, PlanItemStatus.PENDING);
        when(planMapper.selectOwnedForUpdate(7L, 1L)).thenReturn(plan);
        when(itemMapper.selectOwnedForUpdate(7L, 99L, 1L)).thenReturn(current);
        when(itemMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(resultingItems);
        when(planMapper.update(isNull(), any())).thenReturn(1);
        when(itemMapper.selectViews(7L, 1L)).thenReturn(views);
        return service.changeItemStatus(7L, 99L, new PlanItemStatusRequest(target));
    }

    private PlanConfirmRequest request(
            String id, List<PlanConfirmItemRequest> items, int total) {
        return new PlanConfirmRequest(id, DATE, 120, total, null, "摘要", items);
    }

    private PlanItemCompleteRequest completion(
            int minutes, String feedback, boolean completeTask) {
        LocalDateTime startedAt = DATE.atTime(9, 0);
        return new PlanItemCompleteRequest(
                startedAt, startedAt.plusMinutes(minutes), feedback, completeTask);
    }

    private PlanConfirmItemRequest itemRequest(
            int sequence, long taskId, int hour, int minutes) {
        return new PlanConfirmItemRequest(
                sequence,
                taskId,
                DATE.atTime(hour, 0),
                DATE.atTime(hour, 0).plusMinutes(minutes),
                minutes,
                "原因");
    }

    private StudyTask task(long id) {
        StudyTask task = new StudyTask();
        task.setId(id);
        task.setUserId(1L);
        task.setSubjectId(3L);
        task.setStatus(TaskStatus.TODO);
        return task;
    }

    private StudyPlan plan(long id, PlanStatus status) {
        StudyPlan plan = new StudyPlan();
        plan.setId(id);
        plan.setUserId(1L);
        plan.setSourceDraftId("d");
        plan.setPlanDate(DATE);
        plan.setAvailableMinutes(120);
        plan.setPlannedMinutes(60);
        plan.setSummary("摘要");
        plan.setStatus(status);
        plan.setCreatedAt(NOW);
        plan.setUpdatedAt(NOW);
        return plan;
    }

    private StudyPlanItem planItem(long id, PlanItemStatus status) {
        StudyPlanItem item = new StudyPlanItem();
        item.setId(id);
        item.setPlanId(7L);
        item.setUserId(1L);
        item.setTaskId(10L);
        item.setStartAt(DATE.atTime(9, 0));
        item.setEndAt(DATE.atTime(10, 0));
        item.setPlannedMinutes(60);
        item.setStatus(status);
        return item;
    }

    private PlanItemView pendingItemView(long id) {
        return itemView(id, null, null, PlanItemStatus.PENDING);
    }

    private PlanItemView skippedItemView(long id) {
        return itemView(id, null, null, PlanItemStatus.SKIPPED);
    }

    private PlanItemView completedItemView(long id, Integer minutes, String feedback) {
        return itemView(id, minutes, feedback, PlanItemStatus.COMPLETED);
    }

    private PlanItemView itemView(
            long id, Integer actualMinutes, String feedback, PlanItemStatus status) {
        return new PlanItemView(
                id,
                Math.toIntExact(id),
                10L,
                "任务",
                3L,
                "科目",
                "#409EFF",
                DATE.atTime(9, 0),
                DATE.atTime(10, 0),
                60,
                actualMinutes,
                feedback,
                "原因",
                status);
    }
}
