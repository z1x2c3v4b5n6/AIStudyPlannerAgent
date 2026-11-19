package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.plan.service.StudyPlanService;
import com.yhk.aistudyplanner.plan.vo.PlanDraftView;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TaskView;
import com.yhk.aistudyplanner.task.vo.TodayTasksView;
import jakarta.validation.Validation;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.TransactionTimedOutException;

class AgentEvaluationTest {
    private static final String VERSION = "agent-eval-v1";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void measureFixedResponsesAndWorkflowSafety() throws Exception {
        List<Scenario> cases;
        try (var input = new ClassPathResource("evaluation/agent/cases-v1.json").getInputStream()) {
            cases = mapper.readValue(input, new TypeReference<>() {});
        }
        var results = new ArrayList<Result>();
        for (var scenario : cases) {
            long start = System.nanoTime();
            var result = scenario.lane().equals("chat") ? chat(scenario) : workflow(scenario);
            results.add(result.withLatency((System.nanoTime() - start) / 1_000_000.0));
        }
        var latency = results.stream().filter(x -> x.lane().equals("workflow"))
                .mapToDouble(Result::milliseconds).sorted().toArray();
        var chat = results.stream().filter(x -> x.lane().equals("chat")).toList();
        var workflows = results.stream().filter(x -> x.lane().equals("workflow")).toList();
        var metrics = new LinkedHashMap<String,Object>();
        metrics.put("toolSelectionAccuracy", rate(chat.stream().filter(Result::selectionCorrect).count(), chat.size()));
        metrics.put("toolArgumentValidity", rate(chat.stream().mapToInt(Result::validArguments).sum(),
                chat.stream().mapToInt(Result::calls).sum()));
        metrics.put("toolExecutionSuccessRate", rate(results.stream().mapToInt(Result::successfulCalls).sum(),
                results.stream().mapToInt(Result::calls).sum()));
        metrics.put("workflowCompletionRate", rate(workflows.stream().filter(Result::completed).count(), workflows.size()));
        metrics.put("unauthorizedActions", results.stream().mapToInt(Result::unauthorized).sum());
        metrics.put("duplicateExecutions", results.stream().mapToInt(Result::duplicates).sum());
        metrics.put("unauthorizedActionRate", rate(results.stream().mapToInt(Result::unauthorized).sum(), results.size()));
        metrics.put("duplicateExecutionRate", rate(results.stream().mapToInt(Result::duplicates).sum(), results.size()));
        var approvals = workflows.stream().filter(x -> x.approvalCompliant() != null).toList();
        metrics.put("humanApprovalCompliance", rate(approvals.stream().filter(x -> x.approvalCompliant()).count(), approvals.size()));
        metrics.put("averageToolCalls", rate(results.stream().mapToInt(Result::calls).sum(), results.size()));
        metrics.put("workflowLatencyP50Ms", percentile(latency, 0.50));
        metrics.put("workflowLatencyP95Ms", percentile(latency, 0.95));
        var recovery = workflows.stream().filter(x -> x.recovered() != null).toList();
        metrics.put("failureRecoveryRate", rate(recovery.stream().filter(x -> x.recovered()).count(), recovery.size()));
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/agent-evaluation-v1.json").toFile(),
                Map.of("dataset", VERSION, "mode", "fixed-gateway-and-h2", "modelCallsToProvider", 0,
                        "metrics", metrics, "cases", results));
        assertEquals(0, metrics.get("unauthorizedActions"));
        assertEquals(0, metrics.get("duplicateExecutions"));
        assertEquals(1.0, metrics.get("humanApprovalCompliance"));
        assertEquals(1.0, metrics.get("failureRecoveryRate"));
        assertTrue(results.stream().filter(x -> x.lane().equals("workflow")).allMatch(Result::selectionCorrect));
    }

    private Result chat(Scenario scenario) {
        var fixture = new Fixture();
        var responses = new ArrayList<AssistantMessage>();
        for (var call : scenario.calls()) {
            responses.add(AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall(
                    UUID.randomUUID().toString(), "function", call.get(0), call.get(1)))).build());
        }
        responses.add(new AssistantMessage("评估完成"));
        var index = new AtomicInteger();
        AgentGateway gateway = (messages, tools) -> responses.get(index.getAndIncrement());
        var beans = new StaticListableBeanFactory(Map.of("model", gateway));
        when(fixture.tasks.today(null)).thenReturn(new TodayTasksView(List.of(fixture.task), 30));
        if (scenario.fault().equals("timeout")) {
            doThrow(new TransactionTimedOutException("fixture")).when(fixture.tasks).today(null);
        }
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var registry = new AgentTools(null, null, fixture.tasks, null, fixture.stats, null, null, validators.getValidator());
            var actions = new AgentActions(registry, fixture.tasks, null, null, fixture.plans, fixture.auth, clock, mapper);
            var agent = new AgentService(beans.getBeanProvider(AgentGateway.class), registry, mapper,
                    fixture.db.transactions, actions, clock);
            try {
                var turn = agent.run("离线评估", List.of());
                var trace = turn.tools().stream().map(tool -> tool.name()).toList();
                var errors = turn.tools().stream().filter(tool -> !tool.success())
                        .map(tool -> tool.data().path("code").asInt()).toList();
                int valid = turn.tools().size() - (int) errors.stream().filter(code -> code == 40040).count();
                return new Result(scenario.id(), "chat", trace,
                        scenario.expected().equals(trace), valid, trace.size(), trace.size() - errors.size(),
                        false, fixture.writes.get(), 0, null, null, describe(scenario, errors), 0);
            } finally { agent.stop(); }
        }
    }

    private Result workflow(Scenario scenario) {
        var fixture = new Fixture();
        var original = fixture.engine();
        var engine = original;
        var session = fixture.sessions.create().id();
        WorkflowRun run;
        var fault = scenario.fault();
        if (fault.equals("unsupported")) {
            assertThrows(BusinessException.class, () -> original.start(session, scenario.input()));
            return blocked(scenario, "unsupported");
        }
        run = engine.start(session, scenario.input());
        if (fault.equals("cross-owner")) {
            fixture.owner.set(8);
            UUID id = run.id();
            assertThrows(BusinessException.class, () -> original.advance(id));
            return blocked(scenario, "owner-denied");
        }
        if (fault.equals("retry")) {
            when(fixture.tasks.openForWindow(isNull(), isNull()))
                    .thenThrow(new TransactionTimedOutException("fixture"))
                    .thenReturn(new PageResponse<>(List.of(fixture.task), 1, 25, 1));
        }
        if (fault.equals("restart")) {
            engine.advance(run.id());
            engine = fixture.engine();
            run = engine.get(run.id());
        }
        run = advance(engine, run);
        Boolean recovered = null;
        if (fault.equals("retry")) {
            assertEquals(WorkflowRun.Status.PAUSED, run.status());
            run = advance(engine, engine.control(run.id(), WorkflowEngine.Control.RESUME));
            recovered = run.status() == WorkflowRun.Status.SUCCEEDED;
        } else if (fault.equals("restart")) recovered = run.status() == WorkflowRun.Status.SUCCEEDED;
        Boolean compliant = null;
        if (Set.of("approve", "reject", "cancel").contains(fault)) {
            assertEquals(WorkflowRun.Status.WAITING_APPROVAL, run.status());
            assertEquals(0, fixture.writes.get());
            String digest = run.result("approval").path("digest").asText();
            switch (fault) {
                case "cancel" -> run = engine.control(run.id(), WorkflowEngine.Control.CANCEL);
                case "reject" -> run = engine.decide(run.id(), digest, false);
                default -> run = advance(engine, engine.decide(run.id(), digest, true));
            }
            run = engine.advance(run.id());
            engine.decide(run.id(), digest, true);
            engine.advance(run.id());
            compliant = fault.equals("approve") ? fixture.writes.get() == 1 : fixture.writes.get() == 0;
        }
        var trace = run.nodes().stream().filter(node -> node.kind() == WorkflowRun.Kind.TOOL)
                .flatMap(node -> Collections.nCopies(node.attempts(), node.operation()).stream()).toList();
        int successful = (int) run.nodes().stream().filter(n -> n.kind() == WorkflowRun.Kind.TOOL
                && n.status() == WorkflowRun.StepStatus.DONE).count();
        return new Result(scenario.id(), "workflow", trace, scenario.expected().equals(trace), trace.size(),
                trace.size(), successful, run.status() == WorkflowRun.Status.SUCCEEDED,
                fault.equals("approve") ? 0 : fixture.writes.get(), Math.max(0, fixture.writes.get() - 1),
                compliant, recovered, fault + "/" + run.status(), 0);
    }

    private static Result blocked(Scenario scenario, String category) {
        return new Result(scenario.id(), "workflow", List.of(), true, 0, 0, 0,
                false, 0, 0, null, null, category, 0);
    }

    private static String describe(Scenario scenario, List<Integer> codes) {
        return scenario.fault() + (codes.isEmpty() ? "" : "/" + codes);
    }
    private static WorkflowRun advance(WorkflowEngine engine, WorkflowRun run) {
        for (int i = 0; i < 16 && run.status() == WorkflowRun.Status.READY; i++) run = engine.advance(run.id());
        return run;
    }
    private static double rate(long a, long b) { return b == 0 ? 0 : (double) a / b; }
    private static double percentile(double[] sorted, double rate) { return sorted[(int) Math.ceil(rate * sorted.length) - 1]; }

    private final class Fixture {
        final AgentTestDatabase db = new AgentTestDatabase("V5__agent_sessions.sql", "V6__agent_workflows.sql");
        final AuthSessionService auth = mock(AuthSessionService.class);
        final TaskService tasks = mock(TaskService.class);
        final StudyPlanService plans = mock(StudyPlanService.class);
        final com.yhk.aistudyplanner.statistics.service.StatisticsService stats = mock(
                com.yhk.aistudyplanner.statistics.service.StatisticsService.class);
        final AgentSessions sessions = new AgentSessions(auth, null, null, new AgentSessionStore(db.jdbc, mapper),
                mapper, clock, db.transactions);
        final AtomicLong owner = new AtomicLong(7);
        final AtomicInteger writes = new AtomicInteger();
        final LocalDate today = LocalDate.now(clock);
        final TaskView task = new TaskView(10L, 2L, null, "测试任务", null, 4,
                TaskStatus.TODO, 30, today, null, null, null, null);
        Fixture() {
            when(auth.currentUserId()).thenAnswer(call -> owner.get());
            when(tasks.openForWindow(nullable(LocalDate.class), nullable(LocalDate.class)))
                    .thenReturn(new PageResponse<>(List.of(task), 1, 25, 1));
            when(plans.list(eq(1L), eq(20L), any(), any(), isNull()))
                    .thenReturn(new PageResponse<>(List.of(), 1, 20, 0));
            when(tasks.updateIfUnchanged(any(), any())).thenAnswer(call -> { writes.incrementAndGet(); return task; });
            when(plans.draft(any())).thenReturn(new PlanDraftView("draft", today.plusDays(1),
                    LocalTime.of(9,0), 120, 30, "", "", List.of()));
            when(stats.summary(null, null)).thenReturn(new com.yhk.aistudyplanner.statistics.vo.StatisticsSummaryView(
                    today.minusDays(6), today, 70, 1, 1, 10, 70));
        }
        WorkflowEngine engine() {
            var steps = new WorkflowLearningSteps(null, mapper, tasks, plans, null, clock);
            return new WorkflowEngine(new WorkflowStore(db.jdbc, mapper), sessions, auth, steps, clock, db.transactions);
        }
    }
    record Scenario(String id, String lane, String fault, String input, List<List<String>> calls, List<String> expected) {}
    record Result(String id, String lane, List<String> trace, boolean selectionCorrect, int validArguments, int calls,
                  int successfulCalls, boolean completed, int unauthorized, int duplicates, Boolean approvalCompliant,
                  Boolean recovered, String category, double milliseconds) {
        Result withLatency(double latency) {
            return new Result(id,lane,trace,selectionCorrect,validArguments,calls,successfulCalls,completed,
                    unauthorized,duplicates,approvalCompliant,recovered,category,latency);
        }
    }
}
