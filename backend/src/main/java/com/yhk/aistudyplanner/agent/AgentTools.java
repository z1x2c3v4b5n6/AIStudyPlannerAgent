package com.yhk.aistudyplanner.agent;

import static com.yhk.aistudyplanner.common.exception.ErrorCode.AGENT_TOOL_INVALID;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.goal.service.GoalService;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import com.yhk.aistudyplanner.learningpath.service.LearningPathService;
import com.yhk.aistudyplanner.record.service.StudyRecordService;
import com.yhk.aistudyplanner.statistics.service.StatisticsService;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TaskView;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
class AgentTools {
    static final int PAGE_SIZE = 5;
    record Empty() {}
    record Page(@Min(1) @Max(1000) Integer page) { int number() { return page == null ? 1 : page; } }
    record TaskFilter(@Min(1) @Max(1000) Integer page, @Size(max=100) String title, Boolean overdueOnly) {}
    record DateArgs(LocalDate date) {}
    record Range(LocalDate startDate, LocalDate endDate, @Min(1) @Max(1000) Integer page) {}
    record Id(@Positive Long id) {}
    record Search(@NotBlank @Size(max=300) String query) {}
    record Result(Object data, List<TaskView> tasks) {
        Result(Object data) { this(data, List.of()); }
    }
    record Tool<T>(String name, String description, Class<T> type, Function<T, Result> execute) {}

    private final Map<String, Tool<?>> tools = new LinkedHashMap<>();
    private final ObjectMapper input = JsonMapper.builder().findAndAddModules()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).build();
    private final Validator validator;

    AgentTools(SubjectService subjects, GoalService goals, TaskService tasks, StudyRecordService records,
               StatisticsService statistics, LearningPathService paths, KnowledgeRetriever knowledge, Validator validator) {
        this.validator = validator;
        register("list_subjects", "查询我的学习科目和科目ID", Empty.class,
                args -> new Result(subjects.list().stream().limit(20).toList()));
        register("list_goals", "分页查询我的学习目标", Page.class,
                args -> new Result(goals.list(args.number(), PAGE_SIZE, null, null)));
        register("today_tasks", "查询某日的实际待办任务，默认今日，含顺延待办", DateArgs.class, args -> {
            var found = tasks.today(args.date());
            var shown = found.tasks().stream().limit(12).toList();
            return new Result(Map.of("tasks", shown, "total", found.tasks().size(),
                    "totalEstimatedMinutes", found.totalEstimatedMinutes()), shown);
        });
        register("open_tasks", "查询未完成任务，支持按标题关键词搜索或仅查已过截止时间的任务", TaskFilter.class, args -> {
            var found = tasks.openTasks(args.page() == null ? 1 : args.page(), PAGE_SIZE,
                    args.title(), Boolean.TRUE.equals(args.overdueOnly()));
            return new Result(found, found.list());
        });
        register("study_records", "分页查询我的学习记录，可传日期区间", Range.class,
                args -> new Result(records.list(args.page() == null ? 1 : args.page(), PAGE_SIZE,
                        null, null, args.startDate(), args.endDate())));
        register("study_statistics", "查询我的学习统计，可传日期区间", Range.class,
                args -> new Result(statistics.summary(args.startDate(), args.endDate())));
        register("learning_paths", "分页查询我的学习路径，传id查询单条执行进度", Id.class,
                args -> new Result(args.id() == null ? paths.list(1, PAGE_SIZE) : paths.get(args.id())));
        register("search_knowledge", "查询本地知识库，返回相关主题与知识点", Search.class,
                args -> new Result(knowledge.search(new KnowledgeSearchRequest(args.query(), null, 4)).stream()
                        .map(hit -> Map.of("topic", hit.unit().topic(), "keyPoints", hit.unit().keyPoints())).toList()));
    }

    private <T> void register(String name, String description, Class<T> type, Function<T, Result> execute) {
        tools.put(name, new Tool<>(name, description, type, execute));
    }

    List<ToolCallback> callbacks() { return tools.values().stream().map(this::callback).toList(); }
    String knownName(String name) { return tools.containsKey(name) ? name : "unknown"; }

    ToolCallback callback(Tool<?> tool) {
        String schema = JsonSchemaGenerator.generateForType(tool.type());
        var definition = ToolDefinition.builder().name(tool.name()).description(tool.description()).inputSchema(schema).build();
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return definition; }
            @Override public String call(String ignored) { throw new IllegalStateException("Manual execution only"); }
        };
    }

    Result run(String name, JsonNode arguments) {
        var tool = tools.get(name);
        if (tool == null) throw new BusinessException(AGENT_TOOL_INVALID);
        return run(tool, arguments);
    }

    private <T> Result run(Tool<T> tool, JsonNode arguments) {
        return tool.execute().apply(bind(arguments, tool.type()));
    }

    ObjectNode parse(String arguments) {
        try {
            if (arguments == null || arguments.length() > 3000 || !(input.readTree(arguments) instanceof ObjectNode object)) {
                throw new BusinessException(AGENT_TOOL_INVALID);
            }
            return object;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BusinessException(AGENT_TOOL_INVALID);
        }
    }

    <T> T bind(JsonNode arguments, Class<T> type) {
        try {
            T value = input.treeToValue(arguments, type);
            if (!validator.validate(value).isEmpty()) throw new BusinessException(AGENT_TOOL_INVALID);
            return value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BusinessException(AGENT_TOOL_INVALID);
        }
    }
}
