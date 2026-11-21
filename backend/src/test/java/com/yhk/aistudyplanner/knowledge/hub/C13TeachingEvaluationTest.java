package com.yhk.aistudyplanner.knowledge.hub;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.yhk.aistudyplanner.assessment.AssessmentModel;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class C13TeachingEvaluationTest {
    @Test void replayReviewedFactsAndConservativeAbstentions() throws Exception {
        var json=new ObjectMapper();
        JsonNode fixture;
        try(var input=getClass().getResourceAsStream("/assessment/teaching-v1.json")) {
            fixture=json.readTree(input);
        }
        var rows=json.createArrayNode();
        int rendered=0, abstained=0, baselineSupported=0;
        for(JsonNode example:fixture.path("cases")) {
            var retrieval=mock(RagRetrieval.class);
            var builder=mock(EvidenceContextBuilder.class);
            var model=mock(AssessmentModel.class);
            var citation=new EvidenceContextBuilder.Block("E1","PUBLIC","Official reference",example.path("version").asText(),
                    null,"document","version","chunk",example.path("url").asText(),5,"Official docs",example.path("excerpt").asText());
            var supplied=new KnowledgeHubSearch.Evidence("PUBLIC","document","version",1,"Official reference","",5,
                    citation.text(),"Official docs",citation.url(),citation.softwareVersion(),null,"OFFICIAL_FETCH",null,List.of("fixture"),10);
            var result=new RagRetrieval.RetrievalResult(List.of(),List.of(supplied),Set.of(citation.softwareVersion()),
                    citation.softwareVersion(),null,KnowledgeHubSearch.OnlineStatus.LOCAL_ONLY);
            when(retrieval.retrieve(anyString(),isNull(),anyString(),eq(RagRetrieval.Scope.PUBLIC),eq(false))).thenReturn(result);
            when(retrieval.current(supplied)).thenReturn(true);
            when(builder.build(result,false,false)).thenReturn(new EvidenceContextBuilder.Context(List.of(citation),Map.of("E1",supplied),500));
            var output=json.createObjectNode();
            var entry=output.putArray("selection").addObject().put("noteId",example.path("note").asText())
                    .put("evidenceId","E1").put("quote",example.path("selection").asText(citation.text()));
            if(example.has("extraClaim")) entry.put("claim",example.path("extraClaim").asText());
            when(model.call(anyString(),any())).thenReturn(new AssessmentModel.Reply(output,null,null));
            long started=System.nanoTime();
            var lesson=new TeachingExplanations(retrieval,builder,model).explain(
                    new TeachingExplanations.Ask(example.path("id").asText(),null,citation.softwareVersion(),TeachingExplanations.Style.EXAMPLE));
            double elapsedMs=(System.nanoTime()-started)/1_000_000.0;
            assertEquals(example.path("expected").asText(),lesson.mode(),example.path("id").asText());
            var sourceOnly=RagAnswerService.selectedSources(List.of(new RagAnswerService.Claim(citation.text(),"E1",citation.text())),List.of(citation));
            if(lesson.lessons().isEmpty()) abstained++;
            else {
                rendered++;
                assertEquals(citation.softwareVersion(),lesson.lessons().get(0).softwareVersion());
                assertEquals("ILLUSTRATIVE_UNVERIFIED",lesson.lessons().get(0).exampleStatus());
                assertEquals(citation.text(),lesson.citations().get(0).quote());
            }
            if(example.path("noRagSupported").asBoolean()) baselineSupported++;
            var row=rows.addObject().put("id",example.path("id").asText()).put("mode",lesson.mode()).put("fixtureLatencyMs",elapsedMs)
                    .put("noRagSupported",example.path("noRagSupported").asBoolean()).put("noRag",example.path("noRag").asText())
                    .put("humanReference",example.path("reference").asText()).put("sourceOnly",sourceOnly.get(0).quote());
            row.set("teaching",json.valueToTree(lesson.lessons()));
        }
        var report=json.createObjectNode().put("dataset",fixture.path("version").asText()).put("mode","OFFLINE_REPLAY")
                .put("groundedExplanations",rendered).put("safeAbstentions",abstained).put("noRagSupported",baselineSupported)
                .put("tokenUsage","NOT_RUN").put("cost","0");
        report.set("cases",rows);
        Files.createDirectories(Path.of("target"));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/c13-teaching-evaluation.json").toFile(),report);
    }
}
