package com.yhk.aistudyplanner.knowledge.service;

import static com.yhk.aistudyplanner.knowledge.service.KnowledgeQueryNormalizer.containsPhrase;

import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult.MatchReason;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult.MatchType;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import com.yhk.aistudyplanner.knowledge.repository.KnowledgeRepository;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeQueryNormalizer.NormalizedQuery;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRetriever {
    private static final int MAX_REASONS = 3;
    private static final Set<String> GENERIC = Set.of(
            "java", "java后端", "ai", "ai应用", "应用", "基础", "面试", "框架", "模型", "流程",
            "理解", "掌握", "常用", "基本概念", "实现原理", "工作方式", "学习", "上下文",
            "the", "and", "for", "with", "output", "input", "model");
    private static final Pattern ENGLISH_WORD = Pattern.compile("[a-z][a-z0-9]{2,}");
    private static final Pattern HAN_RUN = Pattern.compile("\\p{IsHan}{3,}");

    private static final Comparator<KnowledgeSearchResult> RESULT_ORDER = Comparator
            .comparingInt(KnowledgeSearchResult::score).reversed()
            .thenComparing(result -> result.unit().domain().name())
            .thenComparing(result -> result.unit().stage())
            .thenComparing(result -> result.unit().topic())
            .thenComparing(result -> result.unit().id());

    private static final Comparator<MatchReason> REASON_ORDER = Comparator
            .comparingInt(MatchReason::points).reversed()
            .thenComparing(MatchReason::type);

    private final KnowledgeRepository repository;
    private final KnowledgeQueryNormalizer normalizer = new KnowledgeQueryNormalizer();

    public KnowledgeRetriever(KnowledgeRepository repository) {
        this.repository = repository;
    }

    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        NormalizedQuery query = normalizer.normalize(request.query());
        if (query.surface().isEmpty()) {
            return List.of();
        }
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

    private Optional<KnowledgeSearchResult> score(KnowledgeUnit unit, NormalizedQuery query) {
        Map<String, MatchReason> evidence = new LinkedHashMap<>();
        add(evidence, query, "topic", List.of(unit.topic()), 36);
        add(evidence, query, "alias", unit.aliases(), 30);
        add(evidence, query, "tag", unit.tags(), 7);
        add(evidence, query, "keyPoint", unit.keyPoints(), 18);
        add(evidence, query, "learningObjective", unit.learningObjectives(), 12);
        add(evidence, query, "prerequisite", unit.prerequisites(), 4);

        List<MatchReason> reasons = evidence.values().stream().sorted(REASON_ORDER)
                .limit(MAX_REASONS).toList();
        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        int score = reasons.stream().mapToInt(MatchReason::points).sum();
        List<String> terms = reasons.stream().map(MatchReason::term).distinct().toList();
        return Optional.of(new KnowledgeSearchResult(unit, score, terms, reasons));
    }

    private void add(Map<String, MatchReason> evidence, NormalizedQuery query,
                     String field, List<String> values, int weight) {
        for (String value : values) {
            NormalizedQuery term = normalizer.normalize(value);
            if (term.surface().isEmpty() || GENERIC.contains(term.canonical())) {
                continue;
            }
            MatchReason reason = match(query, term, field, weight);
            if (reason != null) {
                evidence.merge(reason.evidence(), reason,
                        (left, right) -> REASON_ORDER.compare(left, right) <= 0 ? left : right);
            }
        }
    }

    private MatchReason match(NormalizedQuery query, NormalizedQuery term, String field, int weight) {
        if (query.surface().equals(term.surface())) {
            return new MatchReason(field, MatchType.EXACT, term.original(), term.canonical(), weight * 4);
        }
        if (query.canonical().equals(term.canonical())) {
            return new MatchReason(field, MatchType.SYNONYM, term.original(), term.canonical(), weight * 3);
        }
        if (containsPhrase(query.surface(), term.surface())) {
            return new MatchReason(field, MatchType.PARTIAL, term.original(), term.canonical(), weight);
        }
        if (containsPhrase(query.canonical(), term.canonical())) {
            return new MatchReason(field, MatchType.SYNONYM, term.original(), term.canonical(), weight * 9 / 10);
        }
        if (weight < 12) {
            return null;
        }
        String fragment = sharedFragment(query.canonical(), term.canonical());
        return fragment.isEmpty() ? null
                : new MatchReason(field, MatchType.PARTIAL, term.original(), fragment,
                        Math.min(weight / 2, 2 * fragment.length()));
    }

    private String sharedFragment(String query, String term) {
        var words = ENGLISH_WORD.matcher(term);
        String best = "";
        while (words.find()) {
            String word = words.group();
            if (word.length() > best.length() && !GENERIC.contains(word) && containsPhrase(query, word)) {
                best = word;
            }
        }

        var hanRuns = HAN_RUN.matcher(term);
        while (hanRuns.find()) {
            int[] run = hanRuns.group().codePoints().toArray();
            for (int length = Math.min(6, run.length); length >= 3 && length > best.length(); length--) {
                for (int start = 0; start + length <= run.length; start++) {
                    String fragment = new String(run, start, length);
                    if (query.contains(fragment) && !GENERIC.contains(fragment)) {
                        best = fragment;
                        break;
                    }
                }
            }
        }
        return best;
    }
}
