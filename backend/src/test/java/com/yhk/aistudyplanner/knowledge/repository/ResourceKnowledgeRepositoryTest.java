package com.yhk.aistudyplanner.knowledge.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;

class ResourceKnowledgeRepositoryTest {
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void loadsJavaBackendAndAiApplicationResources() {
        var repository = new ResourceKnowledgeRepository(objectMapper, new DefaultResourceLoader());

        assertAll(
                () -> assertFalse(repository.findByDomain(KnowledgeDomain.JAVA_BACKEND).isEmpty()),
                () -> assertFalse(repository.findByDomain(KnowledgeDomain.AI_APPLICATION).isEmpty()),
                () -> assertEquals(2, repository.packs().size()),
                () -> assertEquals(KnowledgeDomain.JAVA_BACKEND, repository.packs().get(0).domain()),
                () -> assertEquals(KnowledgeDomain.AI_APPLICATION, repository.packs().get(1).domain()),
                () -> assertEquals("HashMap", repository.findById("java-hashmap").orElseThrow().topic()));
    }

    @Test
    void loadedCollectionsAreImmutableAndIdsAreGloballyUnique() {
        var repository = new ResourceKnowledgeRepository(objectMapper, new DefaultResourceLoader());
        var ids = new HashSet<String>();

        assertTrue(repository.findAll().stream().allMatch(unit -> ids.add(unit.id())));
        assertTrue(repository.findAll().stream().allMatch(unit -> !unit.topic().isBlank()));
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll().clear());
        assertThrows(UnsupportedOperationException.class, () -> repository.packs().clear());
    }

    @Test
    void invalidJsonFailsClearly() {
        var invalid = resource("invalid.json", "{not-json");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ResourceKnowledgeRepository(objectMapper, List.of(invalid)));

        assertTrue(error.getMessage().contains("invalid.json"));
        assertNotNull(error.getCause());
    }

    @Test
    void duplicateIdsAcrossPacksFailClearly() {
        var javaPack = resource("java.json", pack("JAVA_BACKEND", "duplicate-unit"));
        var aiPack = resource("ai.json", pack("AI_APPLICATION", "duplicate-unit"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ResourceKnowledgeRepository(objectMapper, List.of(javaPack, aiPack)));

        assertTrue(error.getMessage().contains("Duplicate knowledge unit id across packs"));
    }

    @Test
    void packRejectsUnitWithDifferentDomain() {
        String mismatched = pack("JAVA_BACKEND", "domain-mismatch")
                .replace("\"domain\":\"JAVA_BACKEND\",\"topic\"", "\"domain\":\"AI_APPLICATION\",\"topic\"");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ResourceKnowledgeRepository(objectMapper, List.of(resource("mismatch.json", mismatched))));

        assertNotNull(error.getCause());
    }

    private ByteArrayResource resource(String name, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), name);
    }

    private String pack(String domain, String id) {
        return """
                {"domain":"%s","name":"Test Pack","version":"1.0","units":[
                  {"id":"%s","domain":"%s","topic":"Test Topic","stage":"Test Stage","difficulty":"BEGINNER",
                   "aliases":[],"prerequisites":[],"learningObjectives":["Understand test topic"],
                   "keyPoints":["Test point"],"tags":["Test"]}
                ]}
                """.formatted(domain, id, domain);
    }
}
