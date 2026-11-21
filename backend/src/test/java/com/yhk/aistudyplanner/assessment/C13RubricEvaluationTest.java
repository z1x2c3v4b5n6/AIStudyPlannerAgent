package com.yhk.aistudyplanner.assessment;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class C13RubricEvaluationTest {
    @Test void compareAdvisoryCoverageAgainstAnIndependentReference() throws Exception {
        var json=new ObjectMapper();
        JsonNode fixture;
        try(var input=getClass().getResourceAsStream("/assessment/short-rubric-v1.json")) { fixture=json.readTree(input); }
        var rows=json.createArrayNode();
        for(var example:fixture.path("cases")) {
            var model=json.createObjectNode().put("factError","UNCERTAIN").put("version","UNCERTAIN");
            var criteria=model.putArray("criteria");
            int covered=0;
            for(int index=0;index<2;index++) {
                String quote=example.path("quotes").path(index).asText();
                if(!quote.isEmpty()) covered++;
                criteria.addObject().put("index",index).put("status",quote.isEmpty()?"UNCERTAIN":"COVERED").put("quote",quote);
            }
            boolean accepted=AssessmentAssistance.valid(model,example.path("answer").asText(),2);
            assertEquals(example.path("expectedAccepted").asBoolean(),accepted);
            rows.addObject().put("id",example.path("id").asText()).put("humanCoverage",example.path("humanCoverage").asInt())
                    .put("fixtureCoverage",covered).put("advisoryAccepted",accepted).put("officialGrade","NEEDS_REVIEW");
        }
        var report=json.createObjectNode().put("dataset",fixture.path("version").asText()).put("mode","OFFLINE_REPLAY");
        report.set("cases",rows);
        Files.createDirectories(Path.of("target"));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/c13-rubric-evaluation.json").toFile(),report);
    }
}
