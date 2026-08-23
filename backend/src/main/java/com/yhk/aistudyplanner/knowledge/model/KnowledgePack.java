package com.yhk.aistudyplanner.knowledge.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record KnowledgePack(
        KnowledgeDomain domain,
        String name,
        String version,
        List<KnowledgeUnit> units) {

    public KnowledgePack {
        domain = Objects.requireNonNull(domain, "Knowledge pack domain is required");
        name = required(name, "name");
        version = required(version, "version");
        if (units == null || units.isEmpty()) {
            throw new IllegalArgumentException("Knowledge pack units must not be empty");
        }
        Set<String> ids = new HashSet<>();
        Set<String> topics = new HashSet<>();
        for (KnowledgeUnit unit : units) {
            Objects.requireNonNull(unit, "Knowledge pack unit must not be null");
            if (unit.domain() != domain) {
                throw new IllegalArgumentException("Knowledge unit domain does not match pack: " + unit.id());
            }
            if (!ids.add(unit.id())) {
                throw new IllegalArgumentException("Duplicate knowledge unit id in pack: " + unit.id());
            }
            if (!topics.add(unit.topic().strip().toLowerCase())) {
                throw new IllegalArgumentException("Duplicate knowledge topic in pack: " + unit.topic());
            }
        }
        units = List.copyOf(units);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Knowledge pack " + field + " is required");
        }
        return value.trim();
    }
}
