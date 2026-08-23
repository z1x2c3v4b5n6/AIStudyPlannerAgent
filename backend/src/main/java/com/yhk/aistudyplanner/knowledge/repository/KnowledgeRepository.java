package com.yhk.aistudyplanner.knowledge.repository;

import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.model.KnowledgePack;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {
    List<KnowledgeUnit> findAll();

    List<KnowledgeUnit> findByDomain(KnowledgeDomain domain);

    Optional<KnowledgeUnit> findById(String id);

    List<KnowledgePack> packs();
}
