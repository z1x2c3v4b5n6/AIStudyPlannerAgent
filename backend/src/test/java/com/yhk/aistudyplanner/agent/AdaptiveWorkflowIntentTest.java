package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;

class AdaptiveWorkflowIntentTest {
    @Test void combinedRequestPersistsClarificationBeforeApprovalAndWrite() {
        var mapper=new ObjectMapper().findAndRegisterModules();
        Clock clock=Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"),ZoneOffset.UTC);
        var steps=new WorkflowLearningSteps(null,mapper,null,null,null,clock);
        steps.setAdaptive(new AdaptiveAgentTools(null,null,null,null,mapper,clock));
        var plan=steps.plan("我最近 Spring Boot 任务落后了，帮我分析问题，然后安排下周计划，再看看 RAG 有没有需要复习的知识。");
        var ids=plan.nodes().stream().map(WorkflowRun.Node::id).toList();
        assertEquals("ADAPTIVE",plan.scenario());
        assertTrue(ids.indexOf("clarify")<ids.indexOf("diagnostics"));
        assertTrue(ids.indexOf("reviewCandidates")<ids.indexOf("adaptiveApproval"));
        assertTrue(ids.indexOf("adaptiveApproval")<ids.indexOf("adaptiveApply"));
        WorkflowEngine.validate(plan.nodes());
        var explicit=steps.plan("我下周每天只有一个小时，把我的任务重新安排一下。");
        assertFalse(explicit.nodes().stream().anyMatch(node->node.kind()==WorkflowRun.Kind.CLARIFY));
        assertEquals(60,explicit.nodes().get(1).input().path("budget").asInt());
    }
}
