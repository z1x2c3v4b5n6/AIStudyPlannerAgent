package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AgentSessionsTest {
    @Test
    void bindsActionsToOwnerDigestAndExpiryAndExecutesOnlyOnce() {
        var auth = mock(AuthSessionService.class);
        when(auth.currentUserId()).thenReturn(1L);
        var clock = mock(Clock.class);
        Instant now = Instant.parse("2026-11-19T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var mapper = new ObjectMapper();
        var agent = mock(AgentService.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var applications = new AtomicInteger();
        var proposal = new AgentActions.Proposal("propose_task_create", "Java", mapper.createObjectNode(),
                applications::incrementAndGet);
        when(agent.run(anyString(), anyList())).thenReturn(new AgentService.Turn("待确认", List.of(), proposal));
        var sessions = new AgentSessions(auth, agent, mapper, clock, transactions);
        var id = sessions.create().id();
        var action = sessions.send(id, "创建任务").actions().get(0);
        when(auth.currentUserId()).thenReturn(2L);
        assertEquals(ErrorCode.AGENT_SESSION_NOT_FOUND,
                assertThrows(BusinessException.class, () -> sessions.decide(id, action.id(), action.digest(), true)).getErrorCode());
        assertTrue(sessions.list().isEmpty());
        when(auth.currentUserId()).thenReturn(1L);
        assertThrows(BusinessException.class, () -> sessions.decide(id, action.id(), "incorrect", true));
        assertEquals(0, applications.get());
        assertEquals(AgentSessions.ActionStatus.EXECUTED,
                sessions.decide(id, action.id(), action.digest(), true).actions().get(0).status());
        sessions.decide(id, action.id(), action.digest(), true);
        assertEquals(1, applications.get());
        var cancelled = sessions.send(id, "创建另一个").actions().get(1);
        sessions.decide(id, cancelled.id(), cancelled.digest(), false);
        assertEquals(AgentSessions.ActionStatus.CANCELLED,
                sessions.decide(id, cancelled.id(), cancelled.digest(), true).actions().get(1).status());
        var expired = sessions.send(id, "再创建一个").actions().get(2);
        when(clock.instant()).thenReturn(now.plusSeconds(601));
        assertEquals(AgentSessions.ActionStatus.EXPIRED,
                sessions.decide(id, expired.id(), expired.digest(), true).actions().get(2).status());
        assertEquals(1, applications.get());
    }
}
