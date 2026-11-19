package com.yhk.aistudyplanner.knowledge.hub;

import java.util.List;

interface KnowledgeSearchProvider {
    List<KnowledgeHub.Source> find(KnowledgeHubSearch.Query query);
}
