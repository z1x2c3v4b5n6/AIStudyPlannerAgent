package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AssessmentAgentApprovalTest {
    @Test void comparesPersistedApprovalByContentAndStillRejectsRealChanges() throws Exception {
        var json=new ObjectMapper();
        var recomputed=json.createObjectNode().put("itemId",12L).put("date","2026-11-27");
        assertTrue(AssessmentAgentTools.samePreview(recomputed,json.readTree("""
                {"date":"2026-11-27","itemId":12}
                """)));
        assertFalse(AssessmentAgentTools.samePreview(recomputed,json.readTree("""
                {"date":"2026-11-27","itemId":13}
                """)));
        assertFalse(AssessmentAgentTools.samePreview(recomputed,json.readTree("""
                {"date":"2026-11-28","itemId":12}
                """)));
    }
}
