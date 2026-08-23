package com.yhk.aistudyplanner.knowledge.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.model.KnowledgePack;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

@Repository
public class ResourceKnowledgeRepository implements KnowledgeRepository {
    private static final List<String> RESOURCE_LOCATIONS = List.of(
            "classpath:/knowledge/java-backend.json",
            "classpath:/knowledge/ai-application.json");

    private final List<KnowledgePack> packs;
    private final List<KnowledgeUnit> units;
    private final Map<KnowledgeDomain, List<KnowledgeUnit>> unitsByDomain;
    private final Map<String, KnowledgeUnit> unitsById;

    @Autowired
    public ResourceKnowledgeRepository(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this(objectMapper, RESOURCE_LOCATIONS.stream().map(resourceLoader::getResource).toList());
    }

    ResourceKnowledgeRepository(ObjectMapper objectMapper, List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            throw new IllegalStateException("No knowledge resources configured");
        }
        List<KnowledgePack> loadedPacks = resources.stream()
                .map(resource -> load(objectMapper, resource))
                .toList();
        Map<String, KnowledgeUnit> byId = new LinkedHashMap<>();
        Map<KnowledgeDomain, List<KnowledgeUnit>> byDomain = new EnumMap<>(KnowledgeDomain.class);
        for (KnowledgePack pack : loadedPacks) {
            for (KnowledgeUnit unit : pack.units()) {
                KnowledgeUnit previous = byId.putIfAbsent(unit.id(), unit);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate knowledge unit id across packs: " + unit.id());
                }
                byDomain.computeIfAbsent(unit.domain(), ignored -> new ArrayList<>()).add(unit);
            }
        }
        Map<KnowledgeDomain, List<KnowledgeUnit>> immutableByDomain = new EnumMap<>(KnowledgeDomain.class);
        byDomain.forEach((domain, domainUnits) -> immutableByDomain.put(domain, List.copyOf(domainUnits)));
        this.packs = List.copyOf(loadedPacks);
        this.units = List.copyOf(byId.values());
        this.unitsByDomain = Map.copyOf(immutableByDomain);
        this.unitsById = Map.copyOf(byId);
    }

    private KnowledgePack load(ObjectMapper objectMapper, Resource resource) {
        if (resource == null || !resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("Knowledge resource is missing or unreadable: " + description(resource));
        }
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(input, KnowledgePack.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load knowledge resource: " + description(resource), exception);
        }
    }

    private String description(Resource resource) {
        return resource == null ? "<null>" : resource.getDescription();
    }

    @Override
    public List<KnowledgeUnit> findAll() {
        return units;
    }

    @Override
    public List<KnowledgeUnit> findByDomain(KnowledgeDomain domain) {
        return unitsByDomain.getOrDefault(domain, List.of());
    }

    @Override
    public Optional<KnowledgeUnit> findById(String id) {
        return Optional.ofNullable(unitsById.get(id));
    }

    @Override
    public List<KnowledgePack> packs() {
        return packs;
    }
}
