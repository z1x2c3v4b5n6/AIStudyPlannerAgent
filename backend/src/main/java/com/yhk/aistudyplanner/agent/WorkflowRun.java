package com.yhk.aistudyplanner.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record WorkflowRun(UUID id, UUID sessionId, String input, String scenario, long revision, Status status,
                   Instant expiresAt, Instant updatedAt, List<Node> nodes) {
    enum Status { READY, RUNNING, PAUSED, WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT }
    enum Kind { START, PLAN, TOOL, CONDITION, APPROVAL, RESULT, END, ERROR }
    enum StepStatus { READY, RUNNING, WAITING, DONE, SKIPPED, FAILED }

    record Node(String id, Kind kind, String operation, List<String> dependsOn, String guard,
                boolean external, JsonNode input, StepStatus status, JsonNode result, String error,
                int attempts, Instant startedAt, Instant endedAt) {
        Node start(Instant at) {
            return new Node(id, kind, operation, dependsOn, guard, external, input, StepStatus.RUNNING,
                    null, null, attempts + 1, at, null);
        }
        Node finish(StepStatus state, JsonNode output, String failure, Instant at) {
            return new Node(id, kind, operation, dependsOn, guard, external, input, state, output, failure,
                    attempts, startedAt, at);
        }
    }

    JsonNode result(String nodeId) {
        var value = nodes.stream().filter(node -> node.id().equals(nodeId)).findFirst().orElseThrow().result();
        return value == null || value.isNull() ? null : value;
    }

    WorkflowRun change(Node node, Status state, Instant now) {
        return new WorkflowRun(id, sessionId, input, scenario, revision, state, expiresAt, now,
                nodes.stream().map(item -> item.id().equals(node.id()) ? node : item).toList());
    }

    WorkflowRun state(Status state, Instant now) {
        return new WorkflowRun(id, sessionId, input, scenario, revision, state, expiresAt, now, nodes);
    }

    WorkflowRun nextVersion() {
        return new WorkflowRun(id, sessionId, input, scenario, revision + 1, status, expiresAt, updatedAt, nodes);
    }
}
