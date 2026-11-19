package com.yhk.aistudyplanner.agent;

import static com.yhk.aistudyplanner.common.exception.ErrorCode.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.goal.service.GoalService;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.service.StudyPlanService;
import com.yhk.aistudyplanner.plan.vo.PlanDraftView;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.dto.TaskCreateRequest;
import com.yhk.aistudyplanner.task.dto.TaskUpdateRequest;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TaskView;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
class AgentActions {
    record Edit(@NotNull @Positive Long taskId, @Size(min=1,max=200) String title,
                @Size(max=1000) String description, @Min(1) @Max(4) Integer priority,
                @Positive @Max(1440) Integer estimatedMinutes, LocalDate plannedDate, LocalDateTime dueAt) {}
    record Plan(@NotNull LocalDate planDate, @NotNull LocalTime startTime,
                @NotNull @Min(15) @Max(720) Integer availableMinutes,
                @Size(max=500) String requirement,
                @NotEmpty @Size(max=8) List<@NotNull @Positive Long> taskIds) {}
    record Proposal(String type, String target, JsonNode preview, Supplier<Object> apply) {}

    private static final String CREATE = "propose_task_create";
    private static final String EDIT = "propose_task_update";
    private static final String PLAN = "propose_study_plan";
    private final AgentTools tools;
    private final TaskService tasks;
    private final SubjectService subjects;
    private final GoalService goals;
    private final StudyPlanService plans;
    private final AuthSessionService session;
    private final Clock clock;
    private final ObjectMapper mapper;

    AgentActions(AgentTools tools, TaskService tasks, SubjectService subjects, GoalService goals,
                 StudyPlanService plans, AuthSessionService session, Clock clock, ObjectMapper mapper) {
        this.tools = tools;
        this.tasks = tasks;
        this.subjects = subjects;
        this.goals = goals;
        this.plans = plans;
        this.session = session;
        this.clock = clock;
        this.mapper = mapper;
    }

    List<ToolCallback> callbacks() {
        return List.of(
                tools.callback(new AgentTools.Tool<>(CREATE, "提出新建任务。使用查询得到的科目ID，需用户确认", TaskCreateRequest.class, null)),
                tools.callback(new AgentTools.Tool<>(EDIT, "提出修改已查询的未完成任务。仅填写要改变的字段，日期ISO格式，需用户确认", Edit.class, null)),
                tools.callback(new AgentTools.Tool<>(PLAN, "基于已查询的真实任务提出每日计划草案，确认后返回草案，保存使用每日计划流程", Plan.class, null)));
    }

    boolean supports(String name) { return CREATE.equals(name) || EDIT.equals(name) || PLAN.equals(name); }

    Proposal propose(String name, JsonNode arguments, Map<Long, TaskView> observed) {
        return switch (name) {
            case CREATE -> create(tools.bind(arguments, TaskCreateRequest.class));
            case EDIT -> edit(tools.bind(arguments, Edit.class), observed);
            case PLAN -> plan(tools.bind(arguments, Plan.class), observed);
            default -> throw new BusinessException(AGENT_TOOL_INVALID);
        };
    }

    private Proposal create(TaskCreateRequest request) {
        validateDates(request.plannedDate(), request.dueAt());
        long user = session.currentUserId();
        var subject = subjects.requireOwned(request.subjectId(), user);
        if (request.goalId() != null) {
            var goal = goals.requireOwned(request.goalId(), user);
            if (!Objects.equals(goal.getSubjectId(), request.subjectId())) throw new BusinessException(GOAL_SUBJECT_MISMATCH);
        }
        if (request.estimatedMinutes() > 1440) throw new BusinessException(AGENT_TOOL_INVALID);
        var preview = Map.of("科目", subject.getName(), "任务", request);
        return new Proposal(CREATE, request.title(), mapper.valueToTree(preview), () -> {
            validateDates(request.plannedDate(), request.dueAt());
            return tasks.create(request);
        });
    }

    private Proposal edit(Edit args, Map<Long, TaskView> observed) {
        TaskView before = requireObserved(args.taskId(), observed);
        validateDates(args.plannedDate(), args.dueAt());
        var request = new TaskUpdateRequest(before.subjectId(), before.goalId(), value(args.title(), before.title()),
                value(args.description(), before.description()), value(args.priority(), before.priority()),
                value(args.estimatedMinutes(), before.estimatedMinutes()), value(args.plannedDate(), before.plannedDate()),
                value(args.dueAt(), before.dueAt()));
        if (request.title().isBlank()) throw new BusinessException(AGENT_TOOL_INVALID);
        var changes = new LinkedHashMap<String, Object>();
        change(changes, "标题", before.title(), request.title());
        change(changes, "描述", before.description(), request.description());
        change(changes, "优先级", before.priority(), request.priority());
        change(changes, "预计分钟", before.estimatedMinutes(), request.estimatedMinutes());
        change(changes, "计划日期", before.plannedDate(), request.plannedDate());
        change(changes, "截止时间", before.dueAt(), request.dueAt());
        if (changes.isEmpty()) throw new BusinessException(AGENT_TOOL_INVALID);
        return new Proposal(EDIT, before.title(), mapper.valueToTree(changes), () -> {
            validateDates(args.plannedDate(), args.dueAt());
            return tasks.updateIfUnchanged(before, request);
        });
    }

    private Proposal plan(Plan args, Map<Long, TaskView> observed) {
        var selected = args.taskIds().stream().map(id -> requireObserved(id, observed)).toList();
        var request = new PlanDraftRequest(args.planDate(), args.startTime(), args.availableMinutes(),
                args.requirement(), selected.stream().map(TaskView::subjectId).distinct().toList(), args.taskIds());
        PlanDraftView preview = plans.draft(request);
        if (preview.items().isEmpty()) throw new BusinessException(PLAN_DRAFT_EMPTY);
        return new Proposal(PLAN, args.planDate().toString(), mapper.valueToTree(preview), () -> {
            for (TaskView expected : selected) requireObserved(expected.id(), observed);
            PlanDraftView confirmed = plans.draft(request);
            if (!preview.items().equals(confirmed.items())) throw new BusinessException(AGENT_ACTION_CONFLICT);
            return confirmed;
        });
    }

    private TaskView requireObserved(Long id, Map<Long, TaskView> observed) {
        var expected = observed.get(id);
        if (expected == null) throw new BusinessException(PLAN_TASK_INVALID);
        var current = tasks.get(id);
        if (!expected.equals(current) || (current.status() != TaskStatus.TODO && current.status() != TaskStatus.IN_PROGRESS)) {
            throw new BusinessException(AGENT_ACTION_CONFLICT);
        }
        return current;
    }

    private void validateDates(LocalDate planned, LocalDateTime due) {
        if (planned != null && planned.isBefore(LocalDate.now(clock))
                || due != null && due.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException(INVALID_DATE_RANGE);
        }
    }

    private static <T> T value(T change, T old) { return change == null ? old : change; }

    private static void change(Map<String, Object> target, String key, Object before, Object after) {
        if (!Objects.equals(before, after)) target.put(key, new Change(before, after));
    }

    private record Change(Object before, Object after) {}
}
