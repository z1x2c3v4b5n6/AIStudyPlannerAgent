package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.learningpath.service.LearningPathAiService;
import com.yhk.aistudyplanner.learningpath.vo.LearningPathDraftView;
import com.yhk.aistudyplanner.plan.service.StudyPlanService;
import com.yhk.aistudyplanner.plan.vo.PlanDraftView;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TaskView;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WorkflowScenariosTest {
    private final LocalDate today = LocalDate.of(2026, 11, 19);
    private final Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AuthSessionService auth = mock(AuthSessionService.class);
    private final AgentTools tools = mock(AgentTools.class);
    private final TaskService tasks = mock(TaskService.class);
    private final StudyPlanService plans = mock(StudyPlanService.class);
    private final LearningPathAiService paths = mock(LearningPathAiService.class);
    private WorkflowEngine engine;
    private UUID session;

    @BeforeEach void setup() {
        when(auth.currentUserId()).thenReturn(7L);
        var db = new AgentTestDatabase("V5__agent_sessions.sql", "V6__agent_workflows.sql");
        var sessions = new AgentSessions(auth, null, null, new AgentSessionStore(db.jdbc, mapper), mapper, clock, db.transactions);
        session = sessions.create().id();
        engine = new WorkflowEngine(new WorkflowStore(db.jdbc, mapper), sessions, auth,
                new WorkflowLearningSteps(tools, mapper, tasks, plans, paths, clock), clock, db.transactions);
    }

    @Test void organizesThisWeekWithoutWrites() {
        when(tasks.openForWindow(today.minusDays(3), today.plusDays(3)))
                .thenReturn(new PageResponse<>(List.of(task(1, 1), task(2, 4)), 1, 25, 2));
        var run = untilBlocked(engine.start(session, "帮我检查这周有哪些任务还没有完成，按紧急程度整理一下。"));
        assertEquals(WorkflowRun.Status.SUCCEEDED, run.status());
        assertEquals(2, run.result("tasks").path("items").get(0).path("id").asInt());
        verify(tasks, never()).updateIfUnchanged(any(), any());
    }

    @Test void rescheduleWaitsForApprovalAndNeverRepeatsCommittedChanges() throws Exception {
        var day = today.plusDays(1);
        var expected = task(2, 4);
        when(tasks.openForWindow(null, day)).thenReturn(new PageResponse<>(List.of(expected), 1, 25, 1));
        when(plans.list(1, 20, day, day, null)).thenReturn(new PageResponse<>(List.of(), 1, 20, 0));
        when(tasks.updateIfUnchanged(eq(expected), any())).thenReturn(expected);
        when(plans.draft(any())).thenReturn(new PlanDraftView("draft", day, LocalTime.of(9, 0), 120, 60, "", "", List.of()));
        var before = untilBlocked(engine.start(session, "我明天只有两个小时，把重要任务重新安排一下。"));
        assertEquals(WorkflowRun.Status.WAITING_APPROVAL, before.status());
        var digest = before.result("approval").path("digest").asText();
        verify(tasks, never()).updateIfUnchanged(any(), any());
        assertThrows(com.yhk.aistudyplanner.common.exception.BusinessException.class,
                () -> engine.decide(before.id(), "bad", true));
        engine.decide(before.id(), digest, true);
        var done = untilBlocked(engine.advance(before.id()));
        assertEquals(WorkflowRun.Status.SUCCEEDED, done.status());
        assertEquals(120, done.result("planDraft").path("availableMinutes").asInt());
        engine.decide(before.id(), digest, true);
        engine.advance(before.id());
        verify(tasks, times(1)).updateIfUnchanged(eq(expected), any());
    }

    @Test void emptyCandidatesSkipApprovalAndWrites() {
        var day = today.plusDays(1);
        when(tasks.openForWindow(null, day)).thenReturn(new PageResponse<>(List.of(), 1, 25, 0));
        when(plans.list(1, 20, day, day, null)).thenReturn(new PageResponse<>(List.of(), 1, 20, 0));
        var run = untilBlocked(engine.start(session, "明天重新安排任务"));
        assertEquals(WorkflowRun.Status.SUCCEEDED, run.status());
        assertFalse(run.result("condition").path("proceed").asBoolean());
        verify(tasks, never()).updateIfUnchanged(any(), any());
    }

    @Test void progressUsesStoredStatsAndRealToolOutputs() {
        when(tasks.openForWindow(null, null)).thenReturn(new PageResponse<>(List.of(task(1, 3)), 1, 25, 1));
        when(tools.run(eq("study_records"), any())).thenReturn(new AgentTools.Result(List.of()));
        when(tools.run(eq("study_statistics"), any())).thenReturn(new AgentTools.Result(Map.of("totalMinutes", 137)));
        when(tools.run(eq("learning_paths"), any())).thenReturn(new AgentTools.Result(List.of()));
        var run = untilBlocked(engine.start(session, "我最近学习进度怎么样，哪些内容落后了？"));
        assertEquals(137, run.result("result").at("/statistics/totalMinutes").asInt());
    }

    @Test void pathGenerationOccursOutsideTransactionAndRequiresApproval() {
        when(tasks.openForWindow(null, null)).thenReturn(new PageResponse<>(List.of(task(1, 3)), 1, 25, 1));
        when(tools.run(eq("list_subjects"), any())).thenReturn(new AgentTools.Result(List.of(Map.of("id", 3, "name", "Spring Boot"))));
        when(tools.run(eq("search_knowledge"), any())).thenReturn(new AgentTools.Result(List.of()));
        when(paths.generate(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new LearningPathDraftView("Spring Boot", "", 3L, "复习", null, null, 60,
                    today, today.plusDays(6), null, 0, List.of());
        });
        var before = untilBlocked(engine.start(session, "我想用一周复习 Spring Boot，你帮我分析已有任务，然后安排学习路线。"));
        assertEquals(WorkflowRun.Status.WAITING_APPROVAL, before.status());
        var finalRun = untilBlocked(engine.decide(before.id(), before.result("approval").path("digest").asText(), false));
        assertEquals(WorkflowRun.Status.CANCELLED, finalRun.status());
        assertNull(finalRun.result("result"));
        verify(paths, times(1)).generate(any());
    }

    private TaskView task(long id, int priority) {
        return new TaskView(id, 3L, null, "任务" + id, null, priority, TaskStatus.TODO, 60,
                today, null, null, null, null);
    }
    private WorkflowRun untilBlocked(WorkflowRun run) {
        for (int i = 0; i < 16 && run.status() == WorkflowRun.Status.READY; i++) run = engine.advance(run.id());
        return run;
    }
}
