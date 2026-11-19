package com.yhk.aistudyplanner.knowledge.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.repository.ResourceKnowledgeRepository;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.DefaultResourceLoader;

/** Explicit offline bridge to the unchanged production keyword implementation. */
@EnabledIfSystemProperty(named = "retrieval.export", matches = "true")
class KnowledgeRetrievalExportTest {
    @Test
    void exportsKeywordRankingsAndLatency() throws Exception {
        var mapper = new ObjectMapper();
        var repository = new ResourceKnowledgeRepository(mapper, new DefaultResourceLoader());
        var retriever = new KnowledgeRetriever(repository);
        var cases = new ArrayList<JsonNode>();
        for (String file : new String[] {
                "src/test/resources/evaluation/knowledge-retrieval-eval.json",
                "../experiments/retrieval/cases.json"}) {
            mapper.readTree(Path.of(file).toFile()).forEach(cases::add);
        }
        var rows = new ArrayList<Map<String, Object>>();
        for (var item : cases) {
            KnowledgeDomain domain = item.path("domain").isTextual()
                    ? KnowledgeDomain.valueOf(item.get("domain").asText()) : null;
            var request = new KnowledgeSearchRequest(item.get("query").asText(), domain, 20);
            for (int run = 0; run < 5; run++) {
                retriever.search(request);
            }
            var timings = new ArrayList<Double>();
            for (int run = 0; run < 20; run++) {
                long start = System.nanoTime();
                retriever.search(request);
                timings.add((System.nanoTime() - start) / 1_000_000.0);
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("id", item.get("id").asText());
            row.put("query", request.query());
            row.put("domain", domain);
            row.put("rankings", retriever.search(request));
            row.put("latencyMs", timings);
            rows.add(row);
        }
        Path output = Path.of("target/retrieval-keyword.json");
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), rows);
    }
}
