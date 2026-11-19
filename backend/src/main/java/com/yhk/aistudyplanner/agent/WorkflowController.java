package com.yhk.aistudyplanner.agent;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent")
public class WorkflowController {
    private final WorkflowEngine engine;
    WorkflowController(WorkflowEngine engine) { this.engine = engine; }

    @PostMapping("/sessions/{session}/workflows")
    public ApiResponse<WorkflowRun> start(@PathVariable UUID session, @Valid @RequestBody AgentController.Input input) {
        return ApiResponse.success(engine.start(session, input.text()));
    }
    @GetMapping("/sessions/{session}/workflows")
    public ApiResponse<List<WorkflowRun>> list(@PathVariable UUID session, @RequestParam(defaultValue="1") int page) {
        return ApiResponse.success(engine.list(session, page));
    }
    @GetMapping("/workflows/{id}")
    public ApiResponse<WorkflowRun> get(@PathVariable UUID id) { return ApiResponse.success(engine.get(id)); }
    @PostMapping("/workflows/{id}/advance")
    public ApiResponse<WorkflowRun> advance(@PathVariable UUID id) { return ApiResponse.success(engine.advance(id)); }
    @PostMapping("/workflows/{id}/decision")
    public ApiResponse<WorkflowRun> decide(@PathVariable UUID id, @Valid @RequestBody WorkflowDecision input) {
        return ApiResponse.success(engine.decide(id, input.digest(), input.confirm(),input.strategy()));
    }
    @PostMapping("/workflows/{id}/control")
    public ApiResponse<WorkflowRun> control(@PathVariable UUID id, @Valid @RequestBody Control input) {
        return ApiResponse.success(engine.control(id, input.command()));
    }
    @PostMapping("/workflows/{id}/clarification")
    public ApiResponse<WorkflowRun> clarify(@PathVariable UUID id,@Valid @RequestBody Clarification input) {
        return ApiResponse.success(engine.clarify(id,input.dailyMinutes(),input.subjectId()));
    }
    record Clarification(@NotNull @Min(15) @Max(720) Integer dailyMinutes,@Positive Long subjectId) {}
    record WorkflowDecision(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String digest,
                            @NotNull Boolean confirm, String strategy) {}
    record Control(@NotNull WorkflowEngine.Control command) {}
}
