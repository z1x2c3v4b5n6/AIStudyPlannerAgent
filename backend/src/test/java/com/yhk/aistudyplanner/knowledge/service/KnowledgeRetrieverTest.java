package com.yhk.aistudyplanner.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.repository.ResourceKnowledgeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class KnowledgeRetrieverTest {
    private KnowledgeRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new KnowledgeRetriever(
                new ResourceKnowledgeRepository(new ObjectMapper(), new DefaultResourceLoader()));
    }

    @Test
    void exactHashMapQueryRanksHashMapFirst() {
        var results = retriever.search(new KnowledgeSearchRequest("HashMap", KnowledgeDomain.JAVA_BACKEND));

        assertEquals("java-hashmap", results.get(0).unit().id());
        assertTrue(results.get(0).matchedTerms().contains("HashMap"));
    }

    @Test
    void javaCollectionsQueryPrioritizesCollectionKnowledgeOverJvmGc() {
        var results = retriever.search(new KnowledgeSearchRequest("Java集合", KnowledgeDomain.JAVA_BACKEND, 12));
        var topics = topics(results);

        assertAll(
                () -> assertTrue(topics.contains("Collection与List体系")),
                () -> assertTrue(topics.contains("ArrayList")),
                () -> assertTrue(topics.contains("HashMap")),
                () -> assertFalse(topics.subList(0, Math.min(8, topics.size())).contains("GC基础与垃圾收集器")));
    }

    @Test
    void threadPoolQueryRanksThreadPoolFirst() {
        var results = retriever.search(new KnowledgeSearchRequest("线程池", KnowledgeDomain.JAVA_BACKEND));

        assertEquals("java-thread-pool", results.get(0).unit().id());
    }

    @Test
    void ragQueryPrioritizesEmbeddingVectorRetrievalAndRag() {
        var results = retriever.search(new KnowledgeSearchRequest(
                "RAG embedding vector retrieval", KnowledgeDomain.AI_APPLICATION, 8));
        var ids = results.stream().map(result -> result.unit().id()).toList();

        assertAll(
                () -> assertTrue(ids.contains("rag-embedding")),
                () -> assertTrue(ids.contains("rag-vector-retrieval")),
                () -> assertTrue(ids.contains("rag-architecture")));
    }

    @Test
    void agentToolCallingQueryIncludesToolAgentAndWorkflowKnowledge() {
        var results = retriever.search(new KnowledgeSearchRequest("AI Agent tool calling", null, 10));
        var ids = results.stream().map(result -> result.unit().id()).toList();

        assertAll(
                () -> assertTrue(ids.contains("ai-tool-function-calling")),
                () -> assertTrue(ids.contains("agent-concepts")),
                () -> assertTrue(ids.contains("agent-workflow")));
    }

    @Test
    void unknownQueryReturnsEmptyResults() {
        assertTrue(retriever.search(new KnowledgeSearchRequest("玄武量子香蕉xyz987", null)).isEmpty());
    }

    @Test
    void explicitDomainNeverLeaksOtherKnowledgePack() {
        var results = retriever.search(new KnowledgeSearchRequest("Java AI Agent", KnowledgeDomain.JAVA_BACKEND, 20));

        assertTrue(results.stream().allMatch(result -> result.unit().domain() == KnowledgeDomain.JAVA_BACKEND));
    }

    @Test
    void identicalInputAlwaysProducesIdenticalOrder() {
        var request = new KnowledgeSearchRequest("Java后端面试重点集合并发JVM", null, 12);
        var expected = retriever.search(request).stream().map(result -> result.unit().id()).toList();

        for (int run = 0; run < 5; run++) {
            assertEquals(expected, retriever.search(request).stream().map(result -> result.unit().id()).toList());
        }
    }

    @Test
    void broadJavaInterviewQueryRetrievesCollectionConcurrencyAndJvmKnowledge() {
        var results = retriever.search(new KnowledgeSearchRequest(
                "Java后端面试重点集合并发JVM", KnowledgeDomain.JAVA_BACKEND, 20));
        var ids = results.stream().map(result -> result.unit().id()).toList();

        assertAll(
                () -> assertTrue(ids.contains("java-hashmap")),
                () -> assertTrue(ids.contains("java-concurrent-hashmap")),
                () -> assertTrue(ids.contains("java-thread-pool")),
                () -> assertTrue(ids.contains("java-synchronized")),
                () -> assertTrue(ids.contains("java-volatile")),
                () -> assertTrue(ids.contains("jvm-memory")),
                () -> assertTrue(ids.contains("jvm-gc")));
    }

    @Test
    void ragAndAgentApplicationQueryReturnsBothKnowledgeAreas() {
        var results = retriever.search(new KnowledgeSearchRequest("学习RAG和Agent应用开发", null, 15));
        var ids = results.stream().map(result -> result.unit().id()).toList();

        assertAll(
                () -> assertTrue(ids.contains("rag-architecture")),
                () -> assertTrue(ids.contains("agent-concepts")),
                () -> assertTrue(ids.contains("agent-workflow")),
                () -> assertTrue(results.stream().allMatch(result -> !result.matchedTerms().isEmpty())));
    }

    @Test
    void invalidLimitIsRejectedAndDefaultIsEight() {
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeSearchRequest("Java", null, 0));
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeSearchRequest("Java", null, 21));
        assertTrue(retriever.search(new KnowledgeSearchRequest("Java", null)).size() <= 8);
    }

    private List<String> topics(List<com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult> results) {
        return results.stream().map(result -> result.unit().topic()).toList();
    }
}
