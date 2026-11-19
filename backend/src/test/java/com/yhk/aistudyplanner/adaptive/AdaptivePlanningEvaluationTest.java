package com.yhk.aistudyplanner.adaptive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanMapper;
import com.yhk.aistudyplanner.plan.service.RuleBasedPlanGenerator;
import com.yhk.aistudyplanner.plan.vo.PlanTaskCandidate;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdaptivePlanningEvaluationTest {
    private static final LocalDate START = LocalDate.of(2026, 4, 20);
    private static final LocalDateTime NOW = START.minusDays(1).atTime(10, 0);
    record Dataset(String version, List<Fixture> cases) {}
    record Fixture(String id, String split, int days, int budget, int occupied, int lockedStart, List<Input> tasks) {}
    record Input(long id, long subject, int priority, int minutes, int day, Integer dueOffset, Long requires) {
        AdaptivePlanner.Task task() {
            return new AdaptivePlanner.Task(id, subject, "task-" + id, priority, minutes, START.plusDays(day),
                    dueOffset == null ? null : START.atTime(9, 0).plusMinutes(dueOffset), null, NOW, requires, false);
        }
    }
    record Result(int scheduled, int fullDuration, int highPriorityOmitted, int conflictSlots, int capacityDays,
                  int moved, List<Long> omitted) {}

    @Test void frozenFixturesCompareTheProductionRulePlannerWithAllAdaptiveStrategies() throws Exception {
        var mapper = new ObjectMapper();
        try (var resource = getClass().getResourceAsStream("/adaptive/planning-v1.json")) {
            var dataset = mapper.readValue(resource, Dataset.class);
            var rows = new ArrayList<Map<String, Object>>();
            for (var fixture : dataset.cases()) {
                var tasks = fixture.tasks().stream().map(Input::task).toList();
                var nine = START.atTime(9, 0);
                var occupied = fixture.occupied() == 0 ? List.<AdaptivePlanner.Slot>of()
                        : List.of(new AdaptivePlanner.Slot(nine, nine.plusMinutes(fixture.occupied())));
                var locks = fixture.lockedStart() == 0 ? List.<AdaptivePlanner.Slot>of()
                        : List.of(new AdaptivePlanner.Slot(nine.plusMinutes(fixture.lockedStart()),
                                nine.plusMinutes(fixture.lockedStart() + 30L)));
                var constraints = new AdaptivePlanner.Constraints(START, fixture.days(), fixture.budget(), null, locks, NOW);
                var scores = new LinkedHashMap<String, Result>();
                scores.put("C8_RULE_PER_DAY", evaluate(fixture, tasks, legacy(fixture, tasks), occupied, locks));
                for (var strategy : AdaptivePlanner.Strategy.values()) {
                    var plan = AdaptivePlanner.plan(strategy, constraints, tasks, occupied);
                    var score = evaluate(fixture, tasks, plan.scheduled(), occupied, locks);
                    assertEquals(0, score.conflictSlots(), fixture.id());
                    assertEquals(0, score.capacityDays(), fixture.id());
                    assertEquals(score.scheduled(), score.fullDuration(), fixture.id());
                    scores.put(strategy.name(), score);
                }
                rows.add(Map.of("id", fixture.id(), "split", fixture.split(), "strategies", scores));
            }
            var report = Path.of("target", "adaptive-planning-v1.json");
            Files.createDirectories(report.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), Map.of("version", dataset.version(), "cases", rows));
        }
    }

    private List<AdaptivePlanner.Placement> legacy(Fixture fixture, List<AdaptivePlanner.Task> tasks) {
        var mapper = mock(StudyPlanMapper.class);
        var generator = new RuleBasedPlanGenerator(mapper, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        var pending = new ArrayList<>(tasks);
        var placements = new ArrayList<AdaptivePlanner.Placement>();
        for (int index = 0; index < fixture.days(); index++) {
            var date = START.plusDays(index);
            var ordered = pending.stream().filter(task -> !task.plannedDate().isAfter(date)
                            || task.dueAt() != null && task.dueAt().isBefore(date.atStartOfDay()))
                    .sorted(Comparator.<AdaptivePlanner.Task>comparingInt(task ->
                                    task.dueAt() != null && task.dueAt().isBefore(date.atStartOfDay()) ? 0 : 1)
                            .thenComparing(Comparator.comparingInt(AdaptivePlanner.Task::priority).reversed())
                            .thenComparing(AdaptivePlanner.Task::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparingLong(AdaptivePlanner.Task::id)).toList();
            if (ordered.isEmpty()) continue;
            when(mapper.selectCandidates(1, date, date.atStartOfDay())).thenReturn(ordered.stream().map(task ->
                    new PlanTaskCandidate(task.id(), task.title(), task.subjectId(), "test", "#ffffff", task.priority(),
                            TaskStatus.TODO, task.minutes(), task.plannedDate(), task.dueAt())).toList());
            var draft = generator.generate(1, new PlanDraftRequest(date, LocalTime.of(9, 0), fixture.budget(), null,
                    ordered.stream().map(AdaptivePlanner.Task::subjectId).distinct().toList(),
                    ordered.stream().map(AdaptivePlanner.Task::id).toList()));
            for (var item : draft.items()) {
                var task = ordered.stream().filter(row -> row.id() == item.taskId()).findFirst().orElseThrow();
                placements.add(new AdaptivePlanner.Placement(task, item.startAt(), item.endAt()));
                pending.remove(task);
            }
        }
        return placements;
    }

    private Result evaluate(Fixture fixture, List<AdaptivePlanner.Task> tasks, List<AdaptivePlanner.Placement> placed,
                            List<AdaptivePlanner.Slot> occupied, List<AdaptivePlanner.Slot> locks) {
        var fixed = new ArrayList<>(occupied);
        fixed.addAll(locks);
        int conflicts = 0, moved = 0, full = 0;
        var usage = new HashMap<LocalDate, Long>();
        for (var slot : occupied) usage.merge(slot.start().toLocalDate(), minutes(slot.start(), slot.end()), Long::sum);
        var omitted = new ArrayList<>(tasks);
        for (var row : placed) {
            if (fixed.stream().anyMatch(slot -> row.start().isBefore(slot.end()) && row.end().isAfter(slot.start()))) conflicts++;
            fixed.add(new AdaptivePlanner.Slot(row.start(), row.end()));
            if (!row.start().toLocalDate().equals(row.task().plannedDate())) moved++;
            var duration = minutes(row.start(), row.end());
            if (duration >= row.task().minutes()) full++;
            usage.merge(row.start().toLocalDate(), duration, Long::sum);
            omitted.remove(row.task());
        }
        return new Result(placed.size(), full, (int) omitted.stream().filter(task -> task.priority() >= 4).count(),
                conflicts, (int) usage.values().stream().filter(count -> count > fixture.budget()).count(),
                moved, omitted.stream().map(AdaptivePlanner.Task::id).toList());
    }

    private long minutes(LocalDateTime from, LocalDateTime to) { return Duration.between(from, to).toMinutes(); }
}
