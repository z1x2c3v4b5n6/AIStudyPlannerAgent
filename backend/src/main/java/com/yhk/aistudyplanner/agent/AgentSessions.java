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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
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
    private static final int MESSAGE_LIMIT = 30;
    private static final int OWNER_LIMIT = 10;
    private static final int GLOBAL_LIMIT = 200;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final AuthSessionService auth;
    private final AgentService agent;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate confirmation;

    AgentSessions(AuthSessionService auth, AgentService agent, ObjectMapper mapper, Clock clock, PlatformTransactionManager transactions) {
        this.auth = auth;
        this.agent = agent;
        this.mapper = mapper;
        this.clock = clock;
        confirmation = new TransactionTemplate(transactions);
        confirmation.setTimeout(5);
    }

    synchronized SessionView create() {
        long owner = auth.currentUserId();
        Instant now = clock.instant();
        sessions.values().removeIf(session -> !session.expiresAt.isAfter(now));
        if (sessions.size() >= GLOBAL_LIMIT
                || sessions.values().stream().filter(session -> session.owner == owner).count() >= OWNER_LIMIT) {
            throw new BusinessException(AGENT_LIMIT);
        }
        var session = new Session(UUID.randomUUID(), owner, now);
        sessions.put(session.id, session);
        return view(session);
    }

    List<SessionSummary> list() {
        long owner = auth.currentUserId();
        Instant now = clock.instant();
        return sessions.values().stream().filter(session -> session.owner == owner && session.expiresAt.isAfter(now))
                .map(session -> new SessionSummary(session.id, session.title, session.changedAt))
                .sorted(Comparator.comparing(SessionSummary::updatedAt).reversed().thenComparing(SessionSummary::id)).toList();
    }

    SessionView get(UUID id) { return access(id, this::view); }

    SessionView send(UUID id, String text) {
        return access(id, session -> {
            if (session.actions.values().stream().anyMatch(action -> action.status == ActionStatus.PENDING)) {
                throw new BusinessException(AGENT_ACTION_CONFLICT);
            }
            var history = session.messages.stream().skip(Math.max(0, session.messages.size() - 12))
                    .<Message>map(message -> message.role().equals("user")
                            ? new UserMessage(message.text()) : new AssistantMessage(historyText(session, message))).toList();
            var turn = agent.run(text, history);
            UUID actionId = null;
            if (turn.proposal() != null) {
                actionId = UUID.randomUUID();
                session.actions.put(actionId, new Action(actionId, turn.proposal(), clock.instant().plus(ACTION_AGE)));
            }
            if (session.messages.isEmpty()) {
                var normalized = text.strip().replaceAll("\\s+", " ");
                session.title = normalized.substring(0, Math.min(32, normalized.length()));
            }
            session.messages.add(new Entry("user", text, List.of(), null));
            session.messages.add(new Entry("assistant", turn.answer(), turn.tools(), actionId));
            trim(session);
            session.changedAt = clock.instant();
            return view(session);
        });
    }

    SessionView decide(UUID sessionId, UUID actionId, String digest, boolean confirm) {
        return access(sessionId, session -> {
            var action = session.actions.get(actionId);
            if (action == null || !action.digest.equals(digest)) throw new BusinessException(AGENT_ACTION_NOT_FOUND);
            if (action.status != ActionStatus.PENDING) return view(session);
            if (!confirm) {
                action.status = ActionStatus.CANCELLED;
            } else {
                // The per-session lock spans the Service transaction. Terminal actions are never executed again.
                action.status = ActionStatus.EXECUTING;
                try {
                    action.result = confirmation.execute(status -> mapper.valueToTree(action.proposal.apply().get()));
                    action.status = ActionStatus.EXECUTED;
                } catch (org.springframework.transaction.TransactionTimedOutException ex) {
                    fail(action, AGENT_TIMEOUT);
                } catch (BusinessException ex) {
                    fail(action, ex.getErrorCode());
                } catch (RuntimeException ex) {
                    fail(action, INTERNAL_ERROR);
                }
            }
            session.changedAt = clock.instant();
            LOG.info("event=agent_action tool={} status={} code={}", action.proposal.type(), action.status, action.errorCode);
            return view(session);
        });
    }

    private static void fail(Action action, ErrorCode error) {
        action.status = ActionStatus.FAILED;
        action.errorCode = error.code();
        action.errorMessage = error.message();
    }

    private <T> T access(UUID id, Function<Session, T> work) {
        var session = sessions.get(id);
        if (session == null || session.owner != auth.currentUserId()) throw new BusinessException(AGENT_SESSION_NOT_FOUND);
        if (!session.lock.tryLock()) throw new BusinessException(AGENT_LIMIT);
        try {
            var now = clock.instant();
            if (!session.expiresAt.isAfter(now) || sessions.get(id) != session) throw new BusinessException(AGENT_SESSION_NOT_FOUND);
            session.expiresAt = now.plus(SESSION_AGE);
            for (var action : session.actions.values()) {
                if (action.status == ActionStatus.PENDING && !action.expiresAt.isAfter(now)) {
                    action.status = ActionStatus.EXPIRED;
                    LOG.info("event=agent_action tool={} status=EXPIRED", action.proposal.type());
                }
            }
            return work.apply(session);
        } finally {
            session.lock.unlock();
        }
    }

    private static String historyText(Session session, Entry message) {
        var action = message.actionId() == null ? null : session.actions.get(message.actionId());
        if (action == null) return message.text();
        return message.text() + "\n上轮操作 " + action.proposal.target() + " 状态：" + action.status;
    }

    private static void trim(Session session) {
        if (session.messages.size() > MESSAGE_LIMIT) {
            for (var old : session.messages.subList(0, session.messages.size() - MESSAGE_LIMIT)) {
                if (old.actionId() != null) session.actions.remove(old.actionId());
            }
            session.messages.subList(0, session.messages.size() - MESSAGE_LIMIT).clear();
        }
    }

    private SessionView view(Session session) {
        return new SessionView(session.id, session.title, session.expiresAt, List.copyOf(session.messages),
                session.actions.values().stream().map(Action::view).toList());
    }

    private static String digest(AgentActions.Proposal proposal) {
        try {
            byte[] value = (proposal.type() + "\n" + proposal.target() + "\n" + proposal.preview())
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    record SessionSummary(UUID id, String title, Instant updatedAt) {}
    record SessionView(UUID id, String title, Instant expiresAt, List<Entry> messages, List<ActionView> actions) {}
    record Entry(String role, String text, List<AgentService.ToolResult> tools, UUID actionId) {}
    record ActionView(UUID id, String type, String target, String digest, Instant expiresAt, ActionStatus status,
                      JsonNode preview, JsonNode result, Integer errorCode, String errorMessage) {}
    enum ActionStatus { PENDING, EXECUTING, EXECUTED, CANCELLED, FAILED, EXPIRED }

    private static final class Session {
        final UUID id;
        final long owner;
        final ReentrantLock lock = new ReentrantLock();
        final List<Entry> messages = new ArrayList<>();
        final Map<UUID, Action> actions = new LinkedHashMap<>();
        volatile Instant expiresAt;
        volatile Instant changedAt;
        volatile String title = "新对话";
        Session(UUID id, long owner, Instant now) {
            this.id = id;
            this.owner = owner;
            changedAt = now;
            expiresAt = now.plus(SESSION_AGE);
        }
    }

    private static final class Action {
        final UUID id;
        final String digest;
        final AgentActions.Proposal proposal;
        final Instant expiresAt;
        ActionStatus status = ActionStatus.PENDING;
        JsonNode result;
        Integer errorCode;
        String errorMessage;
        Action(UUID id, AgentActions.Proposal proposal, Instant expiresAt) {
            this.id = id;
            this.proposal = proposal;
            this.expiresAt = expiresAt;
            digest = AgentSessions.digest(proposal);
        }
        ActionView view() {
            return new ActionView(id, proposal.type(), proposal.target(), digest, expiresAt, status,
                    proposal.preview(), result, errorCode, errorMessage);
        }
    }
}
