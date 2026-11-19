package com.yhk.aistudyplanner.agent;

import static com.yhk.aistudyplanner.common.exception.ErrorCode.AGENT_TOOL_INVALID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class WorkflowLearningSteps {
    record Plan(String scenario, List<WorkflowRun.Node> nodes) {}
    record Definition(WorkflowRun.Kind kind, String operation, String guard) {}
    private final AgentTools tools;
    private final ObjectMapper mapper;

    WorkflowLearningSteps(AgentTools tools, ObjectMapper mapper) {
        this.tools = tools;
        this.mapper = mapper;
    }

    Plan plan(String text) {
        if (text == null || text.isBlank() || text.length() > AgentService.MAX_INPUT
                || !text.matches("(?s).*(任务|待办|未完成).*")) throw new BusinessException(AGENT_TOOL_INVALID);
        return build("ORGANIZE", List.of(def("START", "start"), def("PLAN", "plan"),
                def("TOOL", "tasks"), def("RESULT", "result"), def("END", "end")));
    }

    Plan build(String scenario, List<Definition> definitions) {
        var nodes = new ArrayList<WorkflowRun.Node>();
        for (var item : definitions) {
            var dependency = nodes.isEmpty() ? List.<String>of() : List.of(nodes.get(nodes.size() - 1).id());
            nodes.add(new WorkflowRun.Node(item.operation(), item.kind(), item.operation(), dependency, item.guard(),
                    isExternal(item.operation()), mapper.createObjectNode(), WorkflowRun.StepStatus.READY,
                    null, null, 0, null, null));
        }
        return new Plan(scenario, List.copyOf(nodes));
    }

    static Definition def(String kind, String operation) {
        return new Definition(WorkflowRun.Kind.valueOf(kind), operation, null);
    }

    static boolean isExternal(String operation) { return false; }

    static boolean allowed(WorkflowRun.Kind kind, String operation, boolean external) {
        return external == isExternal(operation) && switch (kind) {
            case START -> operation.equals("start");
            case PLAN -> operation.equals("plan");
            case TOOL -> operation.equals("tasks");
            case CONDITION -> operation.equals("condition");
            case APPROVAL -> operation.equals("approval");
            case RESULT -> operation.equals("result");
            case END -> operation.equals("end");
            case ERROR -> operation.equals("error");
        };
    }

    JsonNode execute(WorkflowRun run, WorkflowRun.Node node) {
        return switch (node.operation()) {
            case "start" -> mapper.valueToTree(Map.of("message", "工作流开始"));
            case "plan" -> mapper.valueToTree(Map.of("scenario", run.scenario()));
            case "tasks" -> mapper.valueToTree(tools.run("open_tasks", mapper.createObjectNode()).data());
            case "result" -> mapper.valueToTree(Map.of("tasks", run.result("tasks")));
            case "end" -> mapper.valueToTree(Map.of("message", "工作流结束"));
            case "error" -> mapper.valueToTree(Map.of("message", "工作流无法继续"));
            default -> throw new BusinessException(AGENT_TOOL_INVALID);
        };
    }
}
