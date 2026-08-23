package com.yhk.aistudyplanner.knowledge.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class KnowledgePackContractTest {
    @Test
    void builtInPacksMeetMinimumContentContract() {
        var repository = new ResourceKnowledgeRepository(new ObjectMapper(), new DefaultResourceLoader());
        var javaUnits = repository.findByDomain(KnowledgeDomain.JAVA_BACKEND);
        var aiUnits = repository.findByDomain(KnowledgeDomain.AI_APPLICATION);

        assertAll(
                () -> assertTrue(javaUnits.size() >= 20),
                () -> assertTrue(aiUnits.size() >= 15),
                () -> assertEquals(repository.findAll().size(),
                        repository.findAll().stream().map(unit -> unit.id()).collect(java.util.stream.Collectors.toSet()).size()));

        var topics = new HashSet<String>();
        repository.findAll().forEach(unit -> assertAll(
                () -> assertFalse(unit.id().isBlank()),
                () -> assertFalse(unit.topic().isBlank()),
                () -> assertTrue(topics.add(unit.domain() + ":" + unit.topic().strip().toLowerCase())),
                () -> assertFalse(unit.tags().isEmpty()),
                () -> assertFalse(unit.learningObjectives().isEmpty()),
                () -> assertFalse(unit.keyPoints().isEmpty())));
    }
}
