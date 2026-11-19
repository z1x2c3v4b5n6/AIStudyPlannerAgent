package com.yhk.aistudyplanner.learningpath.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GenerationRubricTest {
    @Test
    void schemaChecksRawTypesAndRejectsWrappingOrUnexpectedFields() {
        String raw = """
                {"title":"学习路径","summary":null,"stages":[{"stageNo":1,"title":"基础",
                "description":null,"items":[{"sequenceNo":1,"topic":"Collection",
                "learningObjective":"区分集合接口与实现","learningMethod":["运行示例"],
                "completionCriteria":["解释运行结果"],"estimatedMinutes":30,"suggestedDay":1,
                "prerequisite":null,"reason":null}]}]}
                """;
        var mapper = new ObjectMapper();
        assertTrue(GenerationRubric.schema(mapper, raw));
        assertFalse(GenerationRubric.schema(mapper, raw.replace("\"stageNo\":1", "\"stageNo\":\"1\"")));
        assertFalse(GenerationRubric.schema(mapper, raw.replace("\"summary\":null", "\"userId\":123")));
        assertFalse(GenerationRubric.schema(mapper, raw.replace("\"summary\":null", "\"summary\":null,\"summary\":null")));
        assertFalse(GenerationRubric.schema(mapper, raw + "{}"));
        assertFalse(GenerationRubric.schema(mapper, "```json\n" + raw + "```"));
    }
}
