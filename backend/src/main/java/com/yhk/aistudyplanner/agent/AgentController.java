package com.yhk.aistudyplanner.agent;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent/sessions")
public class AgentController {
    private final AgentSessions sessions;

    AgentController(AgentSessions sessions) { this.sessions = sessions; }

    @PostMapping
    public ApiResponse<AgentSessions.SessionView> create() {
        return ApiResponse.success(sessions.create());
    }

    @GetMapping
    public ApiResponse<List<AgentSessions.SessionSummary>> list() {
        return ApiResponse.success(sessions.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentSessions.SessionView> get(@PathVariable UUID id) {
        return ApiResponse.success(sessions.get(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<AgentSessions.SessionView> send(@PathVariable UUID id, @Valid @RequestBody Input input) {
        return ApiResponse.success(sessions.send(id, input.text()));
    }

    @PostMapping("/{id}/actions/{actionId}/decision")
    public ApiResponse<AgentSessions.SessionView> decide(@PathVariable UUID id, @PathVariable UUID actionId,
                                                        @Valid @RequestBody Decision decision) {
        return ApiResponse.success(sessions.decide(id, actionId, decision.digest(), decision.confirm()));
    }

    record Input(@NotBlank @Size(max=AgentService.MAX_INPUT) String text) {}
    record Decision(@NotNull Boolean confirm, @NotBlank @Pattern(regexp="[a-f0-9]{64}") String digest) {}
}
