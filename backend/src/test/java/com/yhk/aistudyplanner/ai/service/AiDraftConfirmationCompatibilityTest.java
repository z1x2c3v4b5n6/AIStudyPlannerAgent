package com.yhk.aistudyplanner.ai.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yhk.aistudyplanner.ai.dto.AiPlanModelResponse;
import com.yhk.aistudyplanner.ai.validator.AiPlanResponseValidator;
import com.yhk.aistudyplanner.ai.vo.AiPlanningContext;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.plan.dto.PlanConfirmItemRequest;
import com.yhk.aistudyplanner.plan.dto.PlanConfirmRequest;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import com.yhk.aistudyplanner.plan.entity.StudyPlan;
import com.yhk.aistudyplanner.plan.entity.StudyPlanItem;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanItemMapper;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanMapper;
import com.yhk.aistudyplanner.plan.service.RuleBasedPlanGenerator;
import com.yhk.aistudyplanner.plan.service.StudyPlanService;
import com.yhk.aistudyplanner.task.entity.StudyTask;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiDraftConfirmationCompatibilityTest {

    @Mock StudyPlanMapper planMapper;
    @Mock StudyPlanItemMapper itemMapper;
    @Mock TaskMapper taskMapper;
    @Mock AuthSessionService session;
    @Mock RuleBasedPlanGenerator ruleGenerator;

    @BeforeEach
    void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "ai-confirm-test");
        TableInfoHelper.initTableInfo(assistant, StudyPlan.class);
        TableInfoHelper.initTableInfo(assistant, StudyPlanItem.class);
        TableInfoHelper.initTableInfo(assistant, StudyTask.class);
    }

    @Test
    void validatedAiDraftCanBeAcceptedByExistingConfirmationService() {
        LocalDate date = LocalDate.of(2026, 7, 23);
        PlanDraftRequest draftRequest = new PlanDraftRequest(date, LocalTime.of(9, 0), 90, null);
        AiPlanningContext.TaskContext taskContext = new AiPlanningContext.TaskContext(
                10L, 2L, "Java", "#409EFF", null, null, "集合练习", null,
                3, TaskStatus.TODO, 60, date, null);
        AiPlanningContext context = new AiPlanningContext(List.of(), List.of(), List.of(taskContext),
                new AiPlanningContext.StudySummaryContext(0, 0, 0), List.of(), List.of());
        var draft = new AiPlanResponseValidator().validate(draftRequest, context,
                new AiPlanModelResponse("按优先级安排", List.of(
                        new AiPlanModelResponse.Item(10L, 60, "优先级较高"))));
        PlanConfirmRequest confirmRequest = new PlanConfirmRequest(
                draft.draftId(), draft.planDate(), draft.availableMinutes(), draft.plannedMinutes(),
                draft.requirement(), draft.summary(), draft.items().stream()
                        .map(item -> new PlanConfirmItemRequest(item.sequenceNo(), item.taskId(), item.startAt(),
                                item.endAt(), item.plannedMinutes(), item.reason()))
                        .toList());

        StudyTask task = new StudyTask();
        task.setId(10L);
        task.setUserId(1L);
        task.setStatus(TaskStatus.TODO);
        when(session.currentUserId()).thenReturn(1L);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(planMapper.insert(any(StudyPlan.class))).thenAnswer(invocation -> {
            StudyPlan plan = invocation.getArgument(0);
            plan.setId(100L);
            return 1;
        });
        when(itemMapper.insert(any(StudyPlanItem.class))).thenReturn(1);
        when(itemMapper.selectViews(100L, 1L)).thenReturn(List.of());
        Clock clock = Clock.fixed(date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai"));
        StudyPlanService service = new StudyPlanService(
                planMapper, itemMapper, taskMapper, session, ruleGenerator, clock);

        var saved = service.confirm(confirmRequest);

        assertEquals(PlanStatus.CONFIRMED, saved.status());
        assertEquals(60, saved.plannedMinutes());
        verify(planMapper).insert(any(StudyPlan.class));
        verify(itemMapper).insert(any(StudyPlanItem.class));
    }
}
