package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class AgentDatabaseConfirmationTest {
    @Test
    void concurrentConfirmationsHaveOneReceiptAndOneBusinessWriteAndFailureRollsBack() throws Exception {
        var db = new AgentTestDatabase("V5__agent_sessions.sql");
        db.jdbc.execute("CREATE TABLE domain_effect(id INT PRIMARY KEY)");
        var auth = mock(AuthSessionService.class);
        when(auth.currentUserId()).thenReturn(7L);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var agent = mock(AgentService.class);
        var actions = mock(AgentActions.class);
        var proposal = new AgentActions.Proposal("propose_task_create", "测试", mapper.createObjectNode(),
                mapper.createObjectNode());
        when(agent.run(anyString(), anyList())).thenReturn(new AgentService.Turn("待确认", List.of(), proposal));
        when(actions.apply(any())).thenAnswer(call -> {
            db.jdbc.update("INSERT INTO domain_effect(id) VALUES(1)");
            return 1;
        });
        var first = new AgentSessions(auth, agent, actions, new AgentSessionStore(db.jdbc, mapper),
                mapper, Clock.systemUTC(), db.transactions);
        var second = new AgentSessions(auth, agent, actions, new AgentSessionStore(db.jdbc, mapper),
                mapper, Clock.systemUTC(), db.transactions);
        var session = first.create().id();
        var pending = first.send(session, "创建").actions().get(0);
        var threads = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var one = threads.submit(() -> { start.await(); return first.decide(session, pending.id(), pending.digest(), true); });
            var two = threads.submit(() -> { start.await(); return second.decide(session, pending.id(), pending.digest(), true); });
            start.countDown();
            assertEquals(AgentSessions.ActionStatus.EXECUTED, one.get(5, TimeUnit.SECONDS).actions().get(0).status());
            assertEquals(AgentSessions.ActionStatus.EXECUTED, two.get(5, TimeUnit.SECONDS).actions().get(0).status());
        } finally { threads.shutdownNow(); }
        assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM agent_action_receipt", Integer.class));
        verify(actions, times(1)).apply(any());
        doAnswer(call -> {
            db.jdbc.update("INSERT INTO domain_effect(id) VALUES(2)");
            throw new BusinessException(ErrorCode.AGENT_ACTION_CONFLICT);
        }).when(actions).apply(any());
        var failing = first.send(session, "还要创建").actions().get(1);
        var result = first.decide(session, failing.id(), failing.digest(), true);
        assertEquals(AgentSessions.ActionStatus.FAILED, result.actions().get(1).status());
        assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM domain_effect", Integer.class));
        assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM agent_action_receipt", Integer.class));
    }
}
