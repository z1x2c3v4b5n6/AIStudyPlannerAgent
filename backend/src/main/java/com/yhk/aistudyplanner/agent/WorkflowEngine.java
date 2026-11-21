package com.yhk.aistudyplanner.agent;

import com.yhk.aistudyplanner.agent.WorkflowRun.Kind;
import com.yhk.aistudyplanner.agent.WorkflowRun.Status;
import com.yhk.aistudyplanner.agent.WorkflowRun.StepStatus;
import static com.yhk.aistudyplanner.common.exception.ErrorCode.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class WorkflowEngine {
    private static final int MAX_STEPS = 16;
    private static final int ATTEMPTS = 2;
    private static final Duration LEASE = Duration.ofSeconds(100);
    private final WorkflowStore store;
    private final AgentSessions sessions;
    private final AuthSessionService auth;
    private final WorkflowLearningSteps steps;
    private final Clock clock;
    private final TransactionTemplate transaction;

    WorkflowEngine(WorkflowStore store, AgentSessions sessions, AuthSessionService auth,
                   WorkflowLearningSteps steps, Clock clock, PlatformTransactionManager manager) {
        this.store = store;
        this.sessions = sessions;
        this.auth = auth;
        this.steps = steps;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
        transaction.setTimeout(5);
    }

    WorkflowRun start(UUID session, String input) {
        sessions.get(session);
        var plan = steps.plan(input);
        validate(plan.nodes());
        var now = clock.instant();
        var run = new WorkflowRun(UUID.randomUUID(), session, input, plan.scenario(), 0,
                Status.READY, now.plus(Duration.ofMinutes(30)), now, plan.nodes());
        store.insert(auth.currentUserId(), run);
        return run;
    }

    WorkflowRun get(UUID id) { return locked(id, run -> run); }

    Function<UUID, WorkflowRun> ownedSnapshots() {
        long owner = auth.currentUserId();
        return id -> store.load(owner, id, false);
    }

    WorkflowRun autoAdvance(UUID id) {
        var run = get(id);
        if (!Set.of("ADAPTIVE","ASSESSMENT").contains(run.scenario())) throw new BusinessException(VALIDATION_ERROR);
        long until = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        for (int index = 0; index < MAX_STEPS && run.status() == Status.READY
                && System.nanoTime() < until; index++) {
            run = advance(id);
        }
        return run;
    }

    List<WorkflowRun> list(UUID session, int page) {
        if (page < 1 || page > 1000) throw new BusinessException(VALIDATION_ERROR);
        // History remains readable when chat becomes inactive.
        return store.list(auth.currentUserId(), session, (long)(page - 1) * 10);
    }

    WorkflowRun advance(UUID id) {
        long owner = auth.currentUserId();
        Claim claim;
        try {
            claim = transaction.execute(status -> tick(owner, refresh(owner, store.load(owner, id, true))));
        } catch (StepFailure failure) {
            return failed(owner, failure.run, failure.node, failure.code);
        }
        if (claim == null || claim.external() == null) return Objects.requireNonNull(claim).run();
        var external = claim.external();
        JsonNode output;
        try {
            output = steps.execute(claim.run(), external);
        } catch (RuntimeException error) {
            return failed(owner, claim.run(), external, code(error));
        }
        return locked(id, run -> {
            if (run.revision() != claim.run().revision() || run.status() != Status.RUNNING) return run;
            return finish(owner, run, external, output);
        });
    }

    WorkflowRun decide(UUID id, String token, boolean approve) { return decide(id,token,approve,null); }

    WorkflowRun decide(UUID id, String token, boolean approve,String strategy) {
        long owner = auth.currentUserId();
        return locked(id, run -> {
            if (run.status() != Status.WAITING_APPROVAL) return run;
            var node = run.nodes().stream().filter(n -> n.status() == StepStatus.WAITING).findFirst().orElseThrow();
            if (node.kind() != Kind.APPROVAL || !node.result().path("digest").asText().equals(token)) {
                throw new BusinessException(AGENT_ACTION_NOT_FOUND);
            }
            JsonNode result=node.result();
            if(approve&&node.operation().equals("adaptiveApproval")){
                if(strategy==null || !Set.of("MINIMAL","PRIORITY","BALANCED").contains(strategy))
                    throw new BusinessException(VALIDATION_ERROR);
                boolean feasible=false;
                for(var scheme:result.path("preview")) if(strategy.equals(scheme.path("strategy").asText())
                        && !scheme.path("scheduled").isEmpty()) feasible=true;
                if(!feasible) throw new BusinessException(PLAN_DRAFT_EMPTY);
                var approved=((com.fasterxml.jackson.databind.node.ObjectNode)result).deepCopy();
                approved.put("strategy",strategy);
                result=approved;
            }
            return store.save(owner, run.change(node.finish(approve ? StepStatus.DONE : StepStatus.SKIPPED,
                    result, null, clock.instant()), approve ? Status.READY : Status.CANCELLED, clock.instant()));
        });
    }

    WorkflowRun clarify(UUID id, int budget,Long subjectId) {
        if(budget<15||budget>720||subjectId!=null&&subjectId<1) throw new BusinessException(VALIDATION_ERROR);
        long owner=auth.currentUserId();
        return locked(id, run->{
            if(run.status()!=Status.WAITING_INPUT) throw new BusinessException(AGENT_ACTION_CONFLICT);
            var plan=run.nodes().stream().filter(node->node.id().equals("plan")).findFirst().orElseThrow();
            var clarification=run.nodes().stream().filter(node->node.kind()==Kind.CLARIFY).findFirst().orElseThrow();
            var input=((com.fasterxml.jackson.databind.node.ObjectNode)plan.input()).deepCopy();
            input.put("budget",budget);
            if(subjectId!=null) input.put("subjectId",subjectId);
            var now=clock.instant();
            var next=run.change(plan.withInput(input),Status.READY,now);
            return store.save(owner,next.change(clarification.finish(StepStatus.DONE,input,null,now),Status.READY,now));
        });
    }

    WorkflowRun control(UUID id, Control command) {
        long owner = auth.currentUserId();
        return locked(id, run -> {
            if (terminal(run.status())) return run;
            return switch (command) {
                case CANCEL -> store.save(owner, run.state(Status.CANCELLED, clock.instant()));
                case PAUSE -> run.status() == Status.READY
                        ? store.save(owner, run.state(Status.PAUSED, clock.instant())) : run;
                case RESUME -> run.status() == Status.PAUSED
                        ? store.save(owner, run.state(Status.READY, clock.instant())) : run;
            };
        });
    }

    private Claim tick(long owner, WorkflowRun run) {
        if (run.status() != Status.READY) return new Claim(run, null);
        var node = run.nodes().stream().filter(candidate -> candidate.status() == StepStatus.READY)
                .filter(candidate -> candidate.dependsOn().stream().allMatch(id -> run.nodes().stream()
                        .anyMatch(dep -> dep.id().equals(id) && (dep.status() == StepStatus.DONE
                                || dep.status() == StepStatus.SKIPPED)))).findFirst();
        if (node.isEmpty()) return new Claim(store.save(owner, run.state(Status.FAILED, clock.instant())), null);
        var selected = node.get();
        var now = clock.instant();
        if (selected.guard() != null && !run.result(selected.guard()).path("proceed").asBoolean(false)) {
            return new Claim(store.save(owner, run.change(selected.finish(StepStatus.SKIPPED, null, null, now),
                    Status.READY, now)), null);
        }
        var started = selected.start(now);
        if (started.external()) return new Claim(store.save(owner, run.change(started, Status.RUNNING, now)), started);
        try {
            return new Claim(finish(owner, run, started, steps.execute(run, started)), null);
        } catch (RuntimeException error) {
            throw new StepFailure(run, started, code(error));
        }
    }

    private WorkflowRun finish(long owner, WorkflowRun run, WorkflowRun.Node node, JsonNode output) {
        var next = switch (node.kind()) {
            case APPROVAL -> Status.WAITING_APPROVAL;
            case CLARIFY -> Status.WAITING_INPUT;
            case END -> Status.SUCCEEDED;
            case ERROR -> Status.FAILED;
            default -> Status.READY;
        };
        return store.save(owner, run.change(node.finish(next == Status.WAITING_APPROVAL || next == Status.WAITING_INPUT
                ? StepStatus.WAITING : StepStatus.DONE, output, null, clock.instant()), next, clock.instant()));
    }

    private WorkflowRun failed(long owner, WorkflowRun expected, WorkflowRun.Node node, ErrorCode error) {
        return locked(expected.id(), run -> {
            if (run.revision() != expected.revision() || terminal(run.status())) return run;
            boolean retry = !Set.of("apply","adaptiveApply","assessmentApply").contains(node.operation()) && node.attempts() < ATTEMPTS
                    && (error == AGENT_TIMEOUT || error == AI_PROVIDER_TIMEOUT || error == AI_PROVIDER_UNAVAILABLE
                        || error == AI_PROVIDER_RATE_LIMITED || error == INTERNAL_ERROR);
            return store.save(owner, run.change(node.finish(retry ? StepStatus.READY : StepStatus.FAILED, null,
                    error.message(), clock.instant()), retry ? Status.PAUSED : Status.FAILED, clock.instant()));
        });
    }

    private WorkflowRun locked(UUID id, Function<WorkflowRun, WorkflowRun> action) {
        long owner = auth.currentUserId();
        return transaction.execute(status -> action.apply(refresh(owner, store.load(owner, id, true))));
    }

    private WorkflowRun refresh(long owner, WorkflowRun run) {
        var now = clock.instant();
        if (terminal(run.status())) return run;
        if (!run.expiresAt().isAfter(now)) return store.save(owner, run.state(Status.TIMED_OUT, now));
        if (run.status() == Status.WAITING_APPROVAL && !run.updatedAt().plus(Duration.ofMinutes(10)).isAfter(now)) {
            return store.save(owner, run.state(Status.TIMED_OUT, now));
        }
        if (run.status() != Status.RUNNING || run.updatedAt().plus(LEASE).isAfter(now)) return run;
        var node = run.nodes().stream().filter(n -> n.status() == StepStatus.RUNNING).findFirst().orElseThrow();
        boolean retry = node.external() && node.attempts() < ATTEMPTS;
        return store.save(owner, run.change(node.finish(retry ? StepStatus.READY : StepStatus.FAILED,
                null, "外部读取未完成", now), retry ? Status.PAUSED : Status.TIMED_OUT, now));
    }

    static void validate(List<WorkflowRun.Node> nodes) {
        if (nodes.isEmpty() || nodes.size() > MAX_STEPS || nodes.get(0).kind() != Kind.START
                || nodes.get(nodes.size() - 1).kind() != Kind.END
                || nodes.stream().filter(WorkflowRun.Node::external).count() > 1) throw new BusinessException(AGENT_TOOL_INVALID);
        var visited = new HashSet<String>();
        var conditions = new HashSet<String>();
        for (var node : nodes) {
            if (visited.contains(node.id()) || !visited.containsAll(node.dependsOn())
                    || node.guard() != null && !conditions.contains(node.guard())
                    || !WorkflowLearningSteps.allowed(node.kind(), node.operation(), node.external())) {
                throw new BusinessException(AGENT_TOOL_INVALID);
            }
            visited.add(node.id());
            if (node.kind() == Kind.CONDITION) conditions.add(node.id());
        }
    }

    private static boolean terminal(Status status) {
        return status == Status.SUCCEEDED || status == Status.FAILED || status == Status.CANCELLED || status == Status.TIMED_OUT;
    }

    private static ErrorCode code(RuntimeException failure) {
        if (failure instanceof BusinessException business) return business.getErrorCode();
        if (failure instanceof org.springframework.transaction.TransactionTimedOutException) return AGENT_TIMEOUT;
        return INTERNAL_ERROR;
    }

    enum Control { PAUSE, RESUME, CANCEL }
    private record Claim(WorkflowRun run, WorkflowRun.Node external) {}
    private static final class StepFailure extends RuntimeException {
        final WorkflowRun run;
        final WorkflowRun.Node node;
        final ErrorCode code;
        StepFailure(WorkflowRun run, WorkflowRun.Node node, ErrorCode code) {
            this.run = run; this.node = node; this.code = code;
        }
    }
}
