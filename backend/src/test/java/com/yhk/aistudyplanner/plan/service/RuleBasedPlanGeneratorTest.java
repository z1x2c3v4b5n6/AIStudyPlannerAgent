package com.yhk.aistudyplanner.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.mapper.StudyPlanMapper;
import com.yhk.aistudyplanner.plan.vo.PlanTaskCandidate;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleBasedPlanGeneratorTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 7, 23);

  @Mock private StudyPlanMapper mapper;
  private RuleBasedPlanGenerator generator;

  @BeforeEach
  void setUp() {
    Clock clock =
        Clock.fixed(
            TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
            ZoneId.of("Asia/Shanghai"));
    generator = new RuleBasedPlanGenerator(mapper, clock);
  }

  @Test
  void selectedTaskWithoutAvailableCandidateIsRejected() {
    when(mapper.selectCandidates(1, TODAY, TODAY.atStartOfDay())).thenReturn(List.of());
    var request = request(120, List.of(1L));

    assertEquals(
        ErrorCode.SUBJECT_ACCESS_DENIED,
        assertThrows(BusinessException.class, () -> generator.generate(1, request)).getErrorCode());
  }

  @Test
  void onlySelectedTasksAreScheduledAndLastTaskIsTruncated() {
    when(mapper.selectCandidates(anyLong(), any(), any()))
        .thenReturn(
            List.of(
                task(1, 90, 4, TaskStatus.TODO),
                task(2, 90, 2, TaskStatus.IN_PROGRESS),
                task(3, 30, 4, TaskStatus.TODO)));

    var result = generator.generate(1, request(120, List.of(1L, 2L)));

    assertEquals(List.of(1L, 2L), result.items().stream().map(item -> item.taskId()).toList());
    assertEquals(List.of(90, 30), result.items().stream().map(item -> item.plannedMinutes()).toList());
    assertEquals(120, result.plannedMinutes());
  }

  @Test
  void stopsWhenRemainingIsBelowFifteenMinutes() {
    when(mapper.selectCandidates(anyLong(), any(), any()))
        .thenReturn(
            List.of(
                task(1, 110, 4, TaskStatus.TODO),
                task(2, 30, 2, TaskStatus.TODO)));

    var result = generator.generate(1, request(120, List.of(1L, 2L)));

    assertEquals(1, result.items().size());
    assertEquals(110, result.plannedMinutes());
  }

  @Test
  void rejectsPastDateAndCrossDay() {
    assertEquals(
        ErrorCode.PLAN_DATE_IN_PAST,
        assertThrows(
                BusinessException.class,
                () ->
                    generator.generate(
                        1,
                        new PlanDraftRequest(
                            TODAY.minusDays(1),
                            LocalTime.NOON,
                            60,
                            null,
                            List.of(2L),
                            List.of(1L))))
            .getErrorCode());
    assertEquals(
        ErrorCode.INVALID_PLAN_TIME,
        assertThrows(
                BusinessException.class,
                () ->
                    generator.generate(
                        1,
                        new PlanDraftRequest(
                            TODAY,
                            LocalTime.of(23, 0),
                            120,
                            null,
                            List.of(2L),
                            List.of(1L))))
            .getErrorCode());
  }

  @Test
  void usesCurrentUserInCandidateQuery() {
    when(mapper.selectCandidates(9, TODAY, TODAY.atStartOfDay()))
        .thenReturn(List.of(task(1, 30, 3, TaskStatus.TODO)));

    generator.generate(9, request(60, List.of(1L)));

    verify(mapper).selectCandidates(9, TODAY, TODAY.atStartOfDay());
  }

  private PlanDraftRequest request(int availableMinutes, List<Long> taskIds) {
    return new PlanDraftRequest(
        TODAY, LocalTime.of(9, 0), availableMinutes, null, List.of(2L), taskIds);
  }

  private PlanTaskCandidate task(
      long id, int minutes, int priority, TaskStatus status) {
    return new PlanTaskCandidate(
        id,
        "任务" + id,
        2L,
        "Java",
        "#409EFF",
        priority,
        status,
        minutes,
        TODAY,
        TODAY.plusDays(1).atStartOfDay());
  }
}
