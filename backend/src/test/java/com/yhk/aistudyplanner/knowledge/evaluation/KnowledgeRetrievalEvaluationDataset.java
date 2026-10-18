package com.yhk.aistudyplanner.knowledge.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

final class KnowledgeRetrievalEvaluationDataset {
    private static final String LOCATION = "evaluation/knowledge-retrieval-eval.json";

    private KnowledgeRetrievalEvaluationDataset() {}

    static List<KnowledgeRetrievalEvaluationCase> load(ObjectMapper mapper) {
        try (InputStream input = new ClassPathResource(LOCATION).getInputStream()) {
            return mapper.readValue(input, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load retrieval evaluation dataset", exception);
        }
    }
}
