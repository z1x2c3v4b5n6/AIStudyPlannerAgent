package com.yhk.aistudyplanner.knowledge.hub;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
class RegisteredSourceSearch implements KnowledgeSearchProvider {
    private final KnowledgeHub hub;

    RegisteredSourceSearch(KnowledgeHub hub) { this.hub=hub; }

    @Override public List<KnowledgeHub.Source> find(KnowledgeHubSearch.Query query) {
        var terms=EvidenceText.terms(query.query());
        return hub.registeredSources().stream().filter(source -> query.domain()==null || query.domain()==source.domain())
                .filter(source -> query.softwareVersion()==null || query.softwareVersion().equalsIgnoreCase(source.softwareVersion()))
                .filter(source -> query.sourceId()==null || query.sourceId().equals(source.id()))
                .filter(source -> query.sourceId()!=null || terms.stream().anyMatch(term ->
                        EvidenceText.normalize(source.name()+" "+source.url()).contains(term)))
                .limit(2).toList();
    }
}
