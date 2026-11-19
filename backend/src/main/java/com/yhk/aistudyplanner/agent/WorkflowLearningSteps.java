package com.yhk.aistudyplanner.agent;

import static com.yhk.aistudyplanner.common.exception.ErrorCode.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathGenerateRequest;
import com.yhk.aistudyplanner.learningpath.service.LearningPathAiService;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import com.yhk.aistudyplanner.plan.service.StudyPlanService;
import com.yhk.aistudyplanner.task.dto.TaskUpdateRequest;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TaskView;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class WorkflowLearningSteps {
    private static final Pattern MINUTES = Pattern.compile("(\\d{1,4})\\s*分钟");
    private static final Pattern HOURS = Pattern.compile("(\\d{1,3}|[一二两三四五六七八九十])\\s*(?:个)?小时");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    private static final String APPROVAL = "approval";
    private static final String CONDITION = "condition";
    record Plan(String scenario, List<WorkflowRun.Node> nodes) {}
    record Definition(WorkflowRun.Kind kind, String operation, String guard) {}
    record Slot(LocalDate date, LocalTime time, int minutes) {}
    record Candidates(long total, List<TaskView> items) {}
    record Adjustment(Slot slot, List<TaskView> selected, List<Map<String, Object>> changes) {}
    private final AgentTools tools;
    private final ObjectMapper mapper;
    private final TaskService tasks;
    private final StudyPlanService plans;
    private final LearningPathAiService paths;
    private final Clock clock;

    WorkflowLearningSteps(AgentTools tools, ObjectMapper mapper, TaskService tasks, StudyPlanService plans,
                          LearningPathAiService paths, Clock clock) {
        this.tools = tools;
        this.mapper = mapper;
        this.tasks = tasks;
        this.plans = plans;
        this.paths = paths;
        this.clock = clock;
    }

    Plan plan(String text) {
        if (text == null || text.isBlank() || text.length() > AgentService.MAX_INPUT) {
            throw new BusinessException(AGENT_TOOL_INVALID);
        }
        // Parse supported intents only. The output graph and write operations are server-owned.
        String scenario;
        List<Definition> work;
        JsonNode inputs = mapper.createObjectNode();
        if (text.matches("(?s).*(学习路线|学习路径|复习.*一周|长期规划).*")) {
            scenario = "LEARNING_PATH";
            work = List.of(def("TOOL", "subjects"), def("TOOL", "tasks"), def("TOOL", "knowledge"),
                    def("TOOL", "pathDraft"), def("APPROVAL", APPROVAL));
        } else if (text.matches("(?s).*(重新安排|调整安排|重排|安排.*重要任务).*")) {
            scenario = "RESCHEDULE";
            inputs = mapper.valueToTree(slot(text));
            work = List.of(def("TOOL", "tasks"), def("TOOL", "calendar"), def("PLAN", "adjustment"),
                    def("CONDITION", CONDITION), guarded("APPROVAL", APPROVAL),
                    guarded("TOOL", "apply"), guarded("TOOL", "planDraft"));
        } else if (text.matches("(?s).*(学习进度|落后|进度分析|学习情况).*")) {
            scenario = "PROGRESS";
            work = List.of(def("TOOL", "tasks"), def("TOOL", "records"),
                    def("TOOL", "statistics"), def("TOOL", "paths"));
        } else if (text.matches("(?s).*(任务|待办|未完成).*")) {
            scenario = "ORGANIZE";
            work = List.of(def("TOOL", "tasks"));
        } else throw new BusinessException(AGENT_TOOL_INVALID);
        var definitions = new ArrayList<>(List.of(def("START", "start"), def("PLAN", "plan")));
        definitions.addAll(work);
        definitions.add(def("RESULT", "result"));
        definitions.add(def("END", "end"));
        return build(scenario, definitions, inputs);
    }

    private Plan build(String scenario, List<Definition> definitions, JsonNode inputs) {
        var nodes = new ArrayList<WorkflowRun.Node>();
        for (var item : definitions) {
            var dependency = nodes.isEmpty() ? List.<String>of() : List.of(nodes.get(nodes.size() - 1).id());
            nodes.add(new WorkflowRun.Node(item.operation(), item.kind(), item.operation(), dependency, item.guard(),
                    isExternal(item.operation()), item.operation().equals("plan") ? inputs : mapper.createObjectNode(),
                    WorkflowRun.StepStatus.READY, null, null, 0, null, null));
        }
        return new Plan(scenario, List.copyOf(nodes));
    }

    private static Definition def(String kind, String operation) {
        return new Definition(WorkflowRun.Kind.valueOf(kind), operation, null);
    }
    private static Definition guarded(String kind, String operation) {
        return new Definition(WorkflowRun.Kind.valueOf(kind), operation, CONDITION);
    }
    private static boolean isExternal(String operation) { return operation.equals("pathDraft"); }

    static boolean allowed(WorkflowRun.Kind kind, String operation, boolean external) {
        return external == isExternal(operation) && switch (kind) {
            case START -> operation.equals("start");
            case PLAN -> Set.of("plan", "adjustment").contains(operation);
            case TOOL -> Set.of("tasks", "subjects", "knowledge", "records", "statistics", "paths",
                    "calendar", "apply", "planDraft", "pathDraft").contains(operation);
            case CONDITION -> operation.equals(CONDITION);
            case APPROVAL -> operation.equals(APPROVAL);
            case RESULT -> operation.equals("result");
            case END -> operation.equals("end");
            case ERROR -> operation.equals("error");
        };
    }

    JsonNode execute(WorkflowRun run, WorkflowRun.Node node) {
        return switch (node.operation()) {
            case "start" -> data(Map.of("message", "工作流开始"));
            case "plan" -> data(Map.of("scenario", run.scenario(), "inputs", node.input()));
            case "tasks" -> candidates(run);
            case "subjects" -> read("list_subjects");
            case "knowledge" -> data(tools.run("search_knowledge", mapper.createObjectNode()
                    .put("query", run.input().substring(0, Math.min(run.input().length(), 300)))).data());
            case "records" -> read("study_records");
            case "statistics" -> read("study_statistics");
            case "paths" -> read("learning_paths");
            case "calendar" -> data(Map.of("available", free(slot(run))));
            case "adjustment" -> data(adjustment(run));
            case CONDITION -> data(Map.of("proceed", !adjustmentOf(run).selected().isEmpty(),
                    "reason", !run.result("calendar").path("available").asBoolean() ? "时段与已有学习计划冲突"
                            : adjustmentOf(run).selected().isEmpty() ? "时段内没有可安排的任务" : "存在满足容量的候选任务"));
            case APPROVAL -> approval(run);
            case "apply" -> apply(run);
            case "planDraft" -> planDraft(run);
            case "pathDraft" -> pathDraft(run);
            case "result" -> summary(run);
            case "end" -> data(Map.of("message", "工作流结束"));
            case "error" -> data(Map.of("message", "工作流无法继续"));
            default -> throw new BusinessException(AGENT_TOOL_INVALID);
        };
    }

    private JsonNode read(String name) {
        return data(tools.run(name, mapper.createObjectNode()).data());
    }

    private JsonNode candidates(WorkflowRun run) {
        var today = LocalDate.now(clock);
        LocalDate start = run.input().contains("这周") || run.input().contains("本周")
                ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : null;
        LocalDate end = start != null ? start.plusDays(6) : run.scenario().equals("RESCHEDULE") ? slot(run).date() : null;
        var page = tasks.openForWindow(start, end);
        var now = LocalDateTime.now(clock);
        var sorted = page.list().stream().sorted(Comparator
                .comparing((TaskView task) -> task.dueAt() == null || !task.dueAt().isBefore(now))
                .thenComparing(TaskView::priority, Comparator.reverseOrder())
                .thenComparing(TaskView::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TaskView::id)).toList();
        return data(new Candidates(page.total(), sorted));
    }

    private Adjustment adjustment(WorkflowRun run) {
        var slot = slot(run);
        var selected = new ArrayList<TaskView>();
        var changes = new ArrayList<Map<String, Object>>();
        if (!run.result("calendar").path("available").asBoolean()) return new Adjustment(slot, selected, changes);
        int remaining = slot.minutes();
        for (var task : candidatesOf(run).items()) {
            if (selected.size() >= 5) break;
            int minutes = Math.max(15, task.estimatedMinutes());
            if (minutes > remaining) continue;
            var end = LocalDateTime.of(slot.date(), slot.time()).plusMinutes(slot.minutes());
            if (task.dueAt() != null && task.dueAt().isAfter(LocalDateTime.now(clock)) && task.dueAt().isBefore(end)) continue;
            selected.add(task);
            remaining -= minutes;
            var change = new LinkedHashMap<String, Object>();
            change.put("taskId", task.id());
            change.put("title", task.title());
            change.put("before", task.plannedDate());
            change.put("after", slot.date());
            change.put("minutes", minutes);
            changes.add(change);
        }
        return new Adjustment(slot, List.copyOf(selected), List.copyOf(changes));
    }

    private boolean free(Slot slot) {
        var page = plans.list(1, 20, slot.date(), slot.date(), null);
        if (page.total() > page.list().size()) return false;
        var start = LocalDateTime.of(slot.date(), slot.time());
        var end = start.plusMinutes(slot.minutes());
        return page.list().stream()
                .filter(plan -> plan.status() != PlanStatus.CANCELLED && plan.status() != PlanStatus.ABANDONED)
                .flatMap(plan -> plans.get(plan.id()).items().stream())
                .noneMatch(item -> item.startAt().isBefore(end) && item.endAt().isAfter(start));
    }

    private JsonNode approval(WorkflowRun run) {
        JsonNode preview;
        if (run.scenario().equals("RESCHEDULE")) {
            var proposal = adjustmentOf(run);
            preview = data(Map.of("slot", proposal.slot(), "changes", proposal.changes()));
        } else preview = run.result("pathDraft");
        var authorization = new AgentActions.Proposal(run.scenario(), run.id().toString(), preview,
                run.scenario().equals("RESCHEDULE") ? run.result("adjustment") : preview);
        return data(Map.of("preview", preview, "digest", AgentSessions.digest(authorization)));
    }

    private JsonNode apply(WorkflowRun run) {
        if (run.nodes().stream().noneMatch(node -> node.kind() == WorkflowRun.Kind.APPROVAL
                && node.status() == WorkflowRun.StepStatus.DONE)) throw new BusinessException(AGENT_ACTION_CONFLICT);
        var draft = adjustmentOf(run);
        if (!free(draft.slot()) || draft.slot().date().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(AGENT_ACTION_CONFLICT);
        }
        var result = new ArrayList<TaskView>();
        for (var task : draft.selected().stream().sorted(Comparator.comparing(TaskView::id)).toList()) {
            if (Objects.equals(task.plannedDate(), draft.slot().date())) {
                if (!task.equals(tasks.get(task.id()))) throw new BusinessException(AGENT_ACTION_CONFLICT);
                continue;
            }
            var update = new TaskUpdateRequest(task.subjectId(), task.goalId(), task.title(), task.description(),
                    task.priority(), task.estimatedMinutes(), draft.slot().date(), task.dueAt());
            result.add(tasks.updateIfUnchanged(task, update));
        }
        return data(Map.of("updated", result));
    }

    private JsonNode planDraft(WorkflowRun run) {
        var draft = adjustmentOf(run);
        var slot = draft.slot();
        var request = new PlanDraftRequest(slot.date(), slot.time(), slot.minutes(), "工作流安排",
                draft.selected().stream().map(TaskView::subjectId).distinct().toList(),
                draft.selected().stream().map(TaskView::id).toList());
        return data(plans.draft(request));
    }

    private JsonNode pathDraft(WorkflowRun run) {
        var today = LocalDate.now(clock);
        var subjectRows = run.result("subjects");
        Long subjectId = null;
        for (var subject : subjectRows) {
            if (run.input().toLowerCase(Locale.ROOT).contains(subject.path("name").asText().toLowerCase(Locale.ROOT))) {
                subjectId = subject.path("id").asLong();
                break;
            }
        }
        var requirement = run.input().substring(0, Math.min(1900, run.input().length()));
        int taskCount = candidatesOf(run).items().size();
        return data(paths.generate(new LearningPathGenerateRequest(requirement, subjectId, null, null, 60,
                today, today.plusDays(6), "已有未完成任务样本数：" + taskCount + "。为现有任务预留复习时间。")));
    }

    private JsonNode summary(WorkflowRun run) {
        var result = mapper.createObjectNode();
        var found = candidatesOf(run);
        result.put("candidateTotal", found.total());
        result.put("examined", found.items().size());
        result.put("overdue", found.items().stream().filter(task -> task.dueAt() != null
                && task.dueAt().isBefore(LocalDateTime.now(clock))).count());
        if (run.scenario().equals("PROGRESS")) {
            result.set("statistics", run.result("statistics"));
            result.set("paths", run.result("paths"));
        } else if (run.scenario().equals("RESCHEDULE")) {
            result.set("condition", run.result(CONDITION));
            result.set("execution", run.result("apply"));
            result.set("planDraft", run.result("planDraft"));
        } else if (run.scenario().equals("LEARNING_PATH")) result.set("pathDraft", run.result("pathDraft"));
        else result.put("message", "查看任务节点中的紧急度排序");
        return result;
    }

    private JsonNode data(Object value) {
        JsonNode result = mapper.valueToTree(value);
        if (result.toString().length() > 64000) throw new BusinessException(AGENT_LIMIT);
        return result;
    }
    private Adjustment adjustmentOf(WorkflowRun run) { return mapper.convertValue(run.result("adjustment"), Adjustment.class); }
    private Candidates candidatesOf(WorkflowRun run) { return mapper.convertValue(run.result("tasks"), Candidates.class); }
    private Slot slot(WorkflowRun run) {
        var node = run.nodes().stream().filter(item -> item.id().equals("plan")).findFirst().orElseThrow();
        return mapper.convertValue(node.input(), Slot.class);
    }

    private Slot slot(String text) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = text.contains("后天") ? today.plusDays(2) : text.contains("今天") ? today : today.plusDays(1);
        var datePart = ISO_DATE.matcher(text);
        var timePart = TIME.matcher(text);
        LocalTime time = text.contains("下午") ? LocalTime.of(14, 0) : LocalTime.of(9, 0);
        try {
            if (datePart.find()) date = LocalDate.parse(datePart.group());
            if (timePart.find()) time = LocalTime.of(Integer.parseInt(timePart.group(1)), Integer.parseInt(timePart.group(2)));
        } catch (DateTimeException ex) { throw new BusinessException(INVALID_PLAN_TIME); }
        var mins = MINUTES.matcher(text);
        var hours = HOURS.matcher(text);
        int budget = 120;
        if (mins.find()) budget = Integer.parseInt(mins.group(1));
        else if (hours.find()) {
            String value = hours.group(1);
            budget = value.matches("\\d+") ? 60 * Integer.parseInt(value) : 60 * (" 一二三四五六七八九十".indexOf(value.replace("两","二")));
        }
        if (budget < 15 || budget > 720 || date.isBefore(today)
                || LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock))
                || time.toSecondOfDay() + budget * 60 >= 86400) throw new BusinessException(INVALID_PLAN_TIME);
        return new Slot(date, time, budget);
    }
}
