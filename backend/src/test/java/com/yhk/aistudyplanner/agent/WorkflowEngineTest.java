package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowEngineTest {
    @Test
    void persistsProgressAcrossInstancesAndRejectsOtherOwners() {
        var db = new AgentTestDatabase("V5__agent_sessions.sql", "V6__agent_workflows.sql");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var auth = mock(AuthSessionService.class);
        when(auth.currentUserId()).thenReturn(1L);
        var clock = Clock.systemUTC();
        var sessions = new AgentSessions(auth, null, null, new AgentSessionStore(db.jdbc, mapper), mapper, clock, db.transactions);
        var tools = mock(AgentTools.class);
        when(tools.run(eq("open_tasks"), any())).thenReturn(new AgentTools.Result(List.of()));
        var steps = new WorkflowLearningSteps(tools, mapper);
        var store = new WorkflowStore(db.jdbc, mapper);
        var first = new WorkflowEngine(store, sessions, auth, steps, clock, db.transactions);
        var id = first.start(sessions.create().id(), "整理任务").id();
        assertEquals(WorkflowRun.StepStatus.DONE, first.advance(id).nodes().get(0).status());
        var restarted = new WorkflowEngine(store, sessions, auth, steps, clock, db.transactions);
        restarted.control(id, WorkflowEngine.Control.PAUSE);
        assertEquals(WorkflowRun.Status.PAUSED, restarted.advance(id).status());
        restarted.control(id, WorkflowEngine.Control.RESUME);
        for (int i = 0; i < 4; i++) restarted.advance(id);
        assertEquals(WorkflowRun.Status.SUCCEEDED, restarted.get(id).status());
        assertEquals(1, restarted.get(id).nodes().get(0).attempts());
        verify(tools).run(eq("open_tasks"), any());
        when(auth.currentUserId()).thenReturn(2L);
        assertThrows(BusinessException.class, () -> restarted.advance(id));
    }

    @Test
    void rejectsDependenciesThatReferenceLaterSteps() {
        var steps = new WorkflowLearningSteps(null, new ObjectMapper());
        var nodes = new java.util.ArrayList<>(steps.plan("整理任务").nodes());
        var source = nodes.get(0);
        nodes.set(0, new WorkflowRun.Node(source.id(), source.kind(), source.operation(), List.of("end"), null,
                false, null, source.status(), null, null, 0, null, null));
        assertThrows(BusinessException.class, () -> WorkflowEngine.validate(nodes));
    }
}
