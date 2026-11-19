package com.yhk.aistudyplanner.learningpath.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.gateway.PlanningGateway;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDifficulty;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import com.yhk.aistudyplanner.knowledge.repository.ResourceKnowledgeRepository;
import com.yhk.aistudyplanner.knowledge.service.*;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathGenerateRequest;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathModelResponse;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

class GenerationEvaluationTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final String RESOURCE = "evaluation/generation/";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ResourceKnowledgeRepository repository = new ResourceKnowledgeRepository(mapper, new DefaultResourceLoader());
    private final KnowledgeRetriever c4 = new KnowledgeRetriever(repository);
    private final LearningPathKnowledgeQueryBuilder queryBuilder = new LearningPathKnowledgeQueryBuilder();
    private final KnowledgeContextFormatter formatter = new KnowledgeContextFormatter();
    private final LearningPathPromptService prompts = new LearningPathPromptService(mapper);

    @Test
    void evaluateGeneration() throws Exception {
        boolean live = Boolean.getBoolean("generation.eval.live");
        byte[] casesFile = read("cases-v1.json");
        byte[] replayFile = read("replay-v1.json");
        List<GenerationRubric.Case> cases = mapper.readValue(casesFile, new TypeReference<>() {});
        JsonNode replay = mapper.readTree(replayFile);
        var results = new ArrayList<Row>();
        var properties = new AiPlanningProperties();
        properties.setEnabled(true);
        properties.setApiKey("offline-evaluation");
        try (var provider = live ? GenerationLiveGateway.open() : null) {
            for (var testCase : cases) {
                var request = new LearningPathGenerateRequest(testCase.requirement(), null, testCase.currentLevel(),
                        testCase.targetLevel(), 60, START, START.plusDays(3), null);
                var query = queryBuilder.build(request);
                var actual = c4.search(new KnowledgeSearchRequest(query.text(), query.domain()));
                var oracle = new ArrayList<>(testCase.oracleIds().stream().map(this::knowledge).toList());
                if (testCase.scenario().equals("knowledge-gap")) oracle.add(note(testCase.noise()));
                var groups = new LinkedHashMap<String, List<KnowledgeSearchResult>>();
                groups.put("BASELINE", List.of());
                groups.put("RAG", actual);
                groups.put("ORACLE", oracle);
                if (replay.path(testCase.id()).has("STRESS")) {
                    var stress = new ArrayList<KnowledgeSearchResult>();
                    stress.add(note(testCase.noise()));
                    stress.addAll(actual.stream().limit(KnowledgeSearchRequest.DEFAULT_LIMIT - 1).toList());
                    groups.put("STRESS", stress);
                }
                String sharedUser = null;
                for (var group : groups.entrySet()) {
                    JsonNode scripted = replay.path(testCase.id()).path(group.getKey());
                    var row = evaluate(testCase, request, properties, group.getKey(), group.getValue(), scripted, provider);
                    if (sharedUser == null) sharedUser = row.userRequest().toString();
                    assertEquals(sharedUser, row.userRequest().toString(), testCase.id());
                    results.add(row);
                }
            }
            if (!live) verifyReplay(results);
            var summary = new LinkedHashMap<String, Object>();
            for (String group : List.of("BASELINE", "RAG", "ORACLE", "STRESS")) {
                summary.put(group, summarize(results.stream().filter(row -> row.group().equals(group)).toList()));
            }
            var report = new LinkedHashMap<String, Object>();
            report.put("protocolVersion", 1);
            report.put("casesSha256", hash(casesFile));
            report.put("replaySha256", live ? null : hash(replayFile));
            report.put("mode", live ? "deepseek" : "fixed-replay");
            report.put("provider", provider == null ? Map.of("networkCalls", 0) : provider.metadata());
            report.put("summary", summary);
            report.put("cases", results);
            var output = Path.of("target", live ? "generation-evaluation-live.json" : "generation-evaluation-replay.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
            System.out.println("C-6 evaluation report: " + output + " " + mapper.writeValueAsString(summary));
        }
    }

    private Row evaluate(GenerationRubric.Case testCase, LearningPathGenerateRequest request,
                         AiPlanningProperties properties, String group, List<KnowledgeSearchResult> results,
                         JsonNode scripted, GenerationLiveGateway provider) throws Exception {
        var context = formatter.format(results);
        var calls = new ArrayList<Call>();
        PlanningGateway gateway = (system, user) -> {
            assertEquals(calls.isEmpty() ? prompts.system() : prompts.retrySystem(), system);
            assertEquals(prompts.user(request, START, "Z", context.text()), user);
            int index = calls.size();
            if (provider == null) assertTrue(scripted.size() > index, "Missing replay attempt " + testCase.id());
            long start = System.nanoTime();
            try {
                JsonNode payload = scripted.path(index);
                String raw = provider != null ? provider.generate(system, user)
                        : payload.isTextual() ? payload.asText() : payload.toString();
                calls.add(new Call(raw, null, elapsed(start)));
                return raw;
            } catch (com.yhk.aistudyplanner.ai.gateway.AiProviderException ex) {
                calls.add(new Call(null, ex.code().name(), elapsed(start)));
                throw ex;
            }
        };
        var factory = new StaticListableBeanFactory(Map.of("evaluationGateway", gateway));
        var service = new LearningPathAiService(properties, factory.getBeanProvider(PlanningGateway.class),
                prompts, mapper, null, null, Clock.fixed(START.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
                group.equals("RAG") ? c4 : mockRetriever(results), queryBuilder, formatter);
        String finalError = null;
        try {
            service.generate(request);
        } catch (BusinessException ex) {
            finalError = ex.getErrorCode().name();
        }
        if (provider == null) assertEquals(scripted.size(), calls.size(), testCase.id() + "/" + group);
        var attempts = calls.stream().map(call -> assess(service, request, testCase, call)).toList();
        var missingIds = testCase.oracleIds().stream().filter(id -> !context.unitIds().contains(id)).toList();
        var extraIds = context.unitIds().stream().filter(id -> !testCase.oracleIds().contains(id)).toList();
        var flags = new ArrayList<String>();
        if (group.equals("RAG") && testCase.scenario().equals("knowledge-gap")) flags.add("knowledge-base-gap");
        if (group.equals("RAG") && !missingIds.isEmpty()) flags.add("relevant-context-missing");
        if (group.equals("RAG") && !extraIds.isEmpty()) flags.add("context-outside-reference-set-review");
        var first = attempts.get(0);
        if (!first.schema() || !first.quality().forbiddenMatches().isEmpty()) flags.add("output-constraint-violation");
        if (first.businessError() != null && first.schema() && first.quality().missingTopics().isEmpty()
                && first.quality().brokenPrerequisites().isEmpty() && first.quality().forbiddenMatches().isEmpty()) {
            flags.add("rubric-content-covered-but-business-rejected");
        }
        if (group.equals("RAG") && missingIds.isEmpty() && !testCase.oracleIds().isEmpty()
                && first.parsed() && !first.quality().missingTopics().isEmpty()) flags.add("context-present-topic-omitted-review");
        if (group.equals("STRESS")) flags.add("synthetic-context-perturbation");
        return new Row(testCase.id(), testCase.scenario(), group,
                mapper.readTree(prompts.user(request, START, "Z", context.text())).get("userRequest"),
                context.unitIds(), context.characterCount(), context.text(), testCase.manualFacts(),
                missingIds, extraIds, flags, attempts, finalError,
                testCase.requiredTopics().size(), testCase.prerequisites().size());
    }

    private Attempt assess(LearningPathAiService service, LearningPathGenerateRequest request,
                           GenerationRubric.Case testCase, Call call) {
        boolean schema = call.raw() != null && GenerationRubric.schema(mapper, call.raw());
        LearningPathModelResponse parsed = null;
        String error = call.providerError();
        if (call.raw() != null) {
            try {
                parsed = service.parse(call.raw());
                service.validate(request, START, parsed);
            } catch (BusinessException ex) {
                error = ex.getErrorCode().name();
            }
        }
        return new Attempt(schema, parsed != null, error, GenerationRubric.score(parsed, testCase),
                call.milliseconds(), call.raw());
    }

    private static Map<String, Object> summarize(List<Row> rows) {
        var summary = new LinkedHashMap<String, Object>();
        summary.put("cases", rows.size());
        summary.put("schemaRate", ratio(rows.stream().filter(row -> row.first().schema()).count(), rows.size()));
        summary.put("firstBusinessPassRate", ratio(rows.stream().filter(row -> row.first().businessError() == null).count(), rows.size()));
        summary.put("finalBusinessPassRate", ratio(rows.stream().filter(row -> row.finalError() == null).count(), rows.size()));
        long topics = rows.stream().mapToLong(Row::topics).sum();
        long edges = rows.stream().mapToLong(Row::edges).sum();
        long nodes = rows.stream().mapToLong(row -> row.first().quality().nodes()).sum();
        summary.put("coverage", ratio(topics - rows.stream().mapToLong(row -> row.first().quality().missingTopics().size()).sum(), topics));
        summary.put("prerequisiteRate", ratio(edges - rows.stream().mapToLong(row -> row.first().quality().brokenPrerequisites().size()).sum(), edges));
        summary.put("duplicateNodeRate", ratio(rows.stream().mapToLong(row -> row.first().quality().duplicateNodes()).sum(), nodes));
        summary.put("unrecognizedTopicProxyRate", ratio(rows.stream().mapToLong(row -> row.first().quality().unrecognizedTopics().size()).sum(), nodes));
        summary.put("forbiddenCases", rows.stream().filter(row -> !row.first().quality().forbiddenMatches().isEmpty()).count());
        var empty = rows.stream().filter(row -> row.contextIds().isEmpty()).toList();
        summary.put("emptyContextCases", empty.size());
        summary.put("emptyContextFirstPassRate", ratio(empty.stream().filter(row -> row.first().businessError() == null).count(), empty.size()));
        summary.put("emptyContextFinalPassRate", ratio(empty.stream().filter(row -> row.finalError() == null).count(), empty.size()));
        summary.put("attempts", rows.stream().mapToInt(row -> row.attempts().size()).sum());
        return summary;
    }

    private static Double ratio(long n, long d) {
        return d == 0 ? null : (double) n / d;
    }

    private static void verifyReplay(List<Row> rows) {
        assertEquals(26, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.finalError() == null));
        assertTrue(rows.stream().anyMatch(row -> row.group().equals("RAG") && row.contextIds().isEmpty()));
        assertTrue(rows.stream().anyMatch(row -> row.attempts().size() == 2 && !row.first().schema()));
        assertTrue(rows.stream().anyMatch(row -> row.flags().contains("rubric-content-covered-but-business-rejected")));
        assertTrue(rows.stream().anyMatch(row -> row.flags().contains("context-present-topic-omitted-review")));
        assertTrue(rows.stream().anyMatch(row -> row.first().quality().duplicateNodes() == 1
                && row.attempts().size() == 2));
        assertTrue(rows.stream().anyMatch(row -> row.group().equals("STRESS") && !row.first().quality().forbiddenMatches().isEmpty()));
        assertTrue(rows.stream().allMatch(row -> row.contextChars() <= 6000 && row.contextIds().size() <= 8));
    }

    private static KnowledgeRetriever mockRetriever(List<KnowledgeSearchResult> results) {
        var retriever = mock(KnowledgeRetriever.class);
        when(retriever.search(any())).thenReturn(results);
        return retriever;
    }

    private KnowledgeSearchResult knowledge(String id) {
        var unit = repository.findAll().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
        return new KnowledgeSearchResult(unit, 1, List.of());
    }

    private static KnowledgeSearchResult note(GenerationRubric.Note note) {
        return new KnowledgeSearchResult(new KnowledgeUnit("eval-note", KnowledgeDomain.JAVA_BACKEND, note.topic(),
                "人工评估资料", KnowledgeDifficulty.BEGINNER, List.of(), List.of(),
                List.of("理解" + note.topic()), List.of(note.keyPoint()), List.of("评估")), 1, List.of());
    }

    private static byte[] read(String name) throws Exception {
        try (var in = new ClassPathResource(RESOURCE + name).getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static double elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    record Call(String raw, String providerError, double milliseconds) {}
    record Attempt(boolean schema, boolean parsed, String businessError, GenerationRubric.Quality quality,
                   double milliseconds, String raw) {}
    record Row(String id, String scenario, String group, JsonNode userRequest, List<String> contextIds,
               int contextChars, String contextText, List<String> manualFacts, List<String> missingReferenceIds, List<String> outsideReferenceIds, List<String> flags,
               List<Attempt> attempts, String finalError, int topics, int edges) {
        Attempt first() { return attempts.get(0); }
    }
}
