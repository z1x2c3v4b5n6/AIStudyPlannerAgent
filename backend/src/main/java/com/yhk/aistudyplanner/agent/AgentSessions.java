package com.yhk.aistudyplanner.agent;

import static com.yhk.aistudyplanner.common.exception.ErrorCode.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AgentSessions {
    private static final Logger LOG = LoggerFactory.getLogger(AgentSessions.class);
    private static final Duration SESSION_AGE = Duration.ofHours(2);
    private static final Duration ACTION_AGE = Duration.ofMinutes(10);
    private final AuthSessionService auth;
    private final AgentService agent;
    private final AgentActions executor;
    private final AgentSessionStore store;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    AgentSessions(AuthSessionService auth, AgentService agent, AgentActions executor, AgentSessionStore store,
                  ObjectMapper mapper, Clock clock, PlatformTransactionManager transactions) {
        this.auth = auth;
        this.agent = agent;
        this.executor = executor;
        this.store = store;
        this.mapper = mapper;
        this.clock = clock;
        tx = new TransactionTemplate(transactions);
        tx.setTimeout(5);
    }

    SessionView create() {
        Instant now = clock.instant();
        store.purge(now.minus(Duration.ofDays(7)), now.minus(Duration.ofDays(30)));
        var row = new Session(UUID.randomUUID(), auth.currentUserId(), 0, "新对话",
                now, now.plus(SESSION_AGE), List.of(), List.of());
        store.insert(row);
        return view(row);
    }

    List<SessionSummary> list() { return list(1, 10); }

    List<SessionSummary> list(int page, int size) {
        if (page < 1 || page > 1000 || size < 1 || size > 50) throw new BusinessException(VALIDATION_ERROR);
        return store.list(auth.currentUserId(), clock.instant(), size, (long)(page - 1) * size);
    }

    void delete(UUID id) {
        if (!store.delete(id, auth.currentUserId())) throw new BusinessException(AGENT_SESSION_NOT_FOUND);
    }

    SessionView get(UUID id) {
        return view(load(id, auth.currentUserId(), false));
    }

    SessionView send(UUID id, String text) {
        long owner = auth.currentUserId();
        var row = load(id, owner, false);
        var actions = new ArrayList<>(row.actions());
        if (actions.stream().anyMatch(action -> action.status() == ActionStatus.PENDING
                || action.status() == ActionStatus.EXECUTING)) throw new BusinessException(AGENT_ACTION_CONFLICT);
        var turn = agent.run(text, history(row));
        var messages = new ArrayList<>(row.messages());
        UUID actionId = null;
        if (turn.proposal() != null) {
            actionId = UUID.randomUUID();
            actions.add(new Action(actionId, digest(turn.proposal()), turn.proposal(),
                    clock.instant().plus(ACTION_AGE), ActionStatus.PENDING, null, null, null));
        }
        messages.add(new Entry("user", text, List.of(), null));
        messages.add(new Entry("assistant", turn.answer(), turn.tools(), actionId));
        if (messages.size() > 30) {
            var old = messages.subList(0, messages.size() - 30);
            var removed = old.stream().map(Entry::actionId).filter(Objects::nonNull).toList();
            actions.removeIf(action -> removed.contains(action.id()));
            old.clear();
        }
        String title = row.messages().isEmpty() ? text.strip().replaceAll("\\s+", " ") : row.title();
        title = title.substring(0, title.offsetByCodePoints(0, Math.min(32, title.codePointCount(0, title.length()))));
        return save(row.changed(title, List.copyOf(messages), List.copyOf(actions), clock.instant()));
    }

    SessionView decide(UUID id, UUID actionId, String digest, boolean confirm) {
        long owner = auth.currentUserId();
        try {
            return tx.execute(status -> decideLocked(id, owner, actionId, digest, confirm));
        } catch (ActionFailure failure) {
            // The transaction containing both the business change and receipt has rolled back.
            return tx.execute(status -> {
                var row = load(id, owner, true);
                var action = matching(row, actionId, digest);
                return action.status() == ActionStatus.PENDING
                        ? replace(row, action.finish(ActionStatus.FAILED, null, failure.error)) : view(row);
            });
        }
    }

    private SessionView decideLocked(UUID id, long owner, UUID actionId, String digest, boolean confirm) {
        var row = load(id, owner, true);
        var action = matching(row, actionId, digest);
        if (action.status() == ActionStatus.EXECUTING) {
            var receipt = store.receipt(actionId, owner, id, digest);
            return replace(row, receipt == null ? action.finish(ActionStatus.FAILED, null, AGENT_ACTION_CONFLICT)
                    : action.finish(ActionStatus.EXECUTED, receipt, null));
        }
        if (action.status() == ActionStatus.EXPIRED) return replace(row, action);
        if (action.status() != ActionStatus.PENDING) return view(row);
        if (!confirm) return replace(row, action.finish(ActionStatus.CANCELLED, null, null));
        try {
            var executing = action.finish(ActionStatus.EXECUTING, null, null);
            // EXECUTING and EXECUTED share this transaction with the domain write and receipt.
            var result = mapper.<JsonNode>valueToTree(executor.apply(executing.proposal()));
            var done = action.finish(ActionStatus.EXECUTED, result, null);
            store.receipt(id, owner, done, clock.instant());
            return replace(row, done);
        } catch (BusinessException ex) {
            throw new ActionFailure(ex.getErrorCode());
        } catch (org.springframework.transaction.TransactionTimedOutException ex) {
            throw new ActionFailure(AGENT_TIMEOUT);
        } catch (RuntimeException ex) {
            throw new ActionFailure(INTERNAL_ERROR);
        }
    }

    private static Action matching(Session row, UUID id, String digest) {
        return row.actions().stream().filter(action -> action.id().equals(id) && action.digest().equals(digest))
                .findFirst().orElseThrow(() -> new BusinessException(AGENT_ACTION_NOT_FOUND));
    }

    private SessionView replace(Session row, Action action) {
        var actions = row.actions().stream().map(a -> a.id().equals(action.id()) ? action : a).toList();
        var updated = save(row.changed(row.title(), row.messages(), actions, clock.instant()));
        LOG.info("event=agent_action tool={} status={} code={}", action.proposal().type(), action.status(), action.errorCode());
        return updated;
    }

    private Session load(UUID id, long owner, boolean lock) {
        var row = store.find(id, owner, lock);
        var now = clock.instant();
        if (row == null || !row.expires().isAfter(now)) throw new BusinessException(AGENT_SESSION_NOT_FOUND);
        var actions = row.actions().stream().map(a -> a.status() == ActionStatus.PENDING && !a.expiresAt().isAfter(now)
                ? a.finish(ActionStatus.EXPIRED, null, null) : a).toList();
        return new Session(row.id(), owner, row.revision(), row.title(), row.updated(), row.expires(), row.messages(), actions);
    }

    private SessionView save(Session row) {
        if (!store.save(row)) throw new BusinessException(AGENT_ACTION_CONFLICT);
        return view(row);
    }

    private static List<Message> history(Session row) {
        var history = new ArrayList<Message>();
        int budget = 4000;
        for (int i = row.messages().size() - 1; i >= 0 && history.size() < 12 && budget > 0; i--) {
            var entry = row.messages().get(i);
            String text = entry.text();
            for (var action : row.actions()) {
                if (action.id().equals(entry.actionId())) text += "\n上轮操作状态：" + action.status();
            }
            text = text.substring(0, Math.min(text.length(), budget));
            budget -= text.length();
            history.add(entry.role().equals("user") ? new UserMessage(text) : new AssistantMessage(text));
        }
        Collections.reverse(history);
        return history;
    }

    private static SessionView view(Session row) {
        return new SessionView(row.id(), row.title(), row.expires(), row.messages(),
                row.actions().stream().map(Action::view).toList());
    }

    static String digest(AgentActions.Proposal proposal) {
        try {
            byte[] value = (proposal.type() + "\n" + proposal.target() + "\n" + proposal.preview()
                    + "\n" + proposal.payload()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    record SessionSummary(UUID id, String title, Instant updatedAt) {}
    record SessionView(UUID id, String title, Instant expiresAt, List<Entry> messages, List<ActionView> actions) {}
    record Entry(String role, String text, List<AgentService.ToolResult> tools, UUID actionId) {}
    record ActionView(UUID id, String type, String target, String digest, Instant expiresAt, ActionStatus status,
                      JsonNode preview, JsonNode result, Integer errorCode, String errorMessage) {}
    enum ActionStatus { PENDING, EXECUTING, EXECUTED, FAILED, CANCELLED, EXPIRED }

    record Session(UUID id, long owner, long revision, String title, Instant updated, Instant expires,
                   List<Entry> messages, List<Action> actions) {
        Session withRevision(long next) { return new Session(id, owner, next, title, updated, expires, messages, actions); }
        Session changed(String title, List<Entry> messages, List<Action> actions, Instant now) {
            return new Session(id, owner, revision, title, now, now.plus(SESSION_AGE), messages, actions);
        }
    }

    record Action(UUID id, String digest, AgentActions.Proposal proposal, Instant expiresAt, ActionStatus status,
                  JsonNode result, Integer errorCode, String errorMessage) {
        Action finish(ActionStatus status, JsonNode data, ErrorCode error) {
            return new Action(id, digest, proposal, expiresAt, status, data, error == null ? null : error.code(),
                    error == null ? null : error.message());
        }
        ActionView view() {
            return new ActionView(id, proposal.type(), proposal.target(), digest, expiresAt, status,
                    proposal.preview(), result, errorCode, errorMessage);
        }
    }
    private static final class ActionFailure extends RuntimeException {
        private final ErrorCode error;
        ActionFailure(ErrorCode error) { this.error = error; }
    }
}
