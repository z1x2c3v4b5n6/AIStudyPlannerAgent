package com.yhk.aistudyplanner.knowledge.service;

import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import com.yhk.aistudyplanner.knowledge.repository.KnowledgeRepository;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRetriever {
    private static final Weight TOPIC = new Weight(10, 8);
    private static final Weight ALIAS = new Weight(8, 6);
    private static final Weight TAG = new Weight(6, 4);
    private static final Weight STAGE = new Weight(4, 3);
    private static final Weight KEY_POINT = new Weight(3, 2);
    private static final Weight OBJECTIVE = new Weight(2, 1);
    private static final Weight PREREQUISITE = new Weight(2, 1);

    private static final Comparator<KnowledgeSearchResult> RESULT_ORDER = Comparator
            .comparingInt(KnowledgeSearchResult::score).reversed()
            .thenComparing(result -> result.unit().domain().name())
            .thenComparing(result -> result.unit().stage())
            .thenComparing(result -> result.unit().topic())
            .thenComparing(result -> result.unit().id());

    private final KnowledgeRepository repository;

    public KnowledgeRetriever(KnowledgeRepository repository) {
        this.repository = repository;
    }

    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        String query = normalize(request.query());
        List<KnowledgeUnit> candidates = request.domain() == null
                ? repository.findAll()
                : repository.findByDomain(request.domain());
        return candidates.stream()
                .map(unit -> score(unit, query))
                .flatMap(Optional::stream)
                .sorted(RESULT_ORDER)
                .limit(request.limit())
                .toList();
    }

    private Optional<KnowledgeSearchResult> score(KnowledgeUnit unit, String query) {
        Score score = new Score();
        score.add(unit.topic(), query, TOPIC);
        unit.aliases().forEach(value -> score.add(value, query, ALIAS));
        unit.tags().forEach(value -> score.add(value, query, TAG));
        score.add(unit.stage(), query, STAGE);
        unit.keyPoints().forEach(value -> score.add(value, query, KEY_POINT));
        unit.learningObjectives().forEach(value -> score.add(value, query, OBJECTIVE));
        unit.prerequisites().forEach(value -> score.add(value, query, PREREQUISITE));
        return score.value == 0
                ? Optional.empty()
                : Optional.of(new KnowledgeSearchResult(unit, score.value, List.copyOf(score.matchedTerms)));
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
        return normalized.trim();
    }

    private record Weight(int exact, int contains) {}

    private static final class Score {
        private int value;
        private final Set<String> matchedTerms = new LinkedHashSet<>();

        private void add(String candidate, String query, Weight weight) {
            String term = normalize(candidate);
            if (term.isEmpty() || query.isEmpty()) {
                return;
            }
            if (query.equals(term)) {
                value += weight.exact();
                matchedTerms.add(candidate);
            } else if (term.length() >= 2 && (query.contains(term) || term.contains(query))) {
                value += weight.contains();
                matchedTerms.add(candidate);
            }
        }
    }
}
