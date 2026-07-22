package com.yhk.aistudyplanner.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yhk.aistudyplanner.ai.mapper.AiPlanningContextMapper;
import com.yhk.aistudyplanner.ai.vo.AiPlanningContext;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiPlanningContextServiceTest {
  @Mock AiPlanningContextMapper mapper;
  AiPlanningContextService service;
  static final LocalDate TODAY = LocalDate.of(2026, 7, 23);

  @BeforeEach
  void setUp() {
    service =
        new AiPlanningContextService(
            mapper,
            Clock.fixed(
                TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai")));
  }

  @Test
  void everyQueryUsesCurrentUserAndTextIsTruncated() {
    String longText = "x".repeat(400);
    var task =
        new AiPlanningContext.TaskContext(
            1L,
            2L,
            "Java",
            "#fff",
            null,
            null,
            "集合",
            longText,
            3,
            TaskStatus.TODO,
            60,
            TODAY,
            null);
    var record =
        new AiPlanningContext.RecordContext(2L, "Java", 1L, "集合", TODAY.atTime(9, 0), 30, longText);
    when(mapper.subjects(9)).thenReturn(List.of());
    when(mapper.goals(9)).thenReturn(List.of());
    when(mapper.tasks(9, TODAY, TODAY.atStartOfDay())).thenReturn(List.of(task));
    when(mapper.summary(eq(9L), any(), any()))
        .thenReturn(new AiPlanningContext.StudySummaryContext(30, 1, 1));
    when(mapper.subjectStudy(eq(9L), any(), any())).thenReturn(List.of());
    when(mapper.records(9)).thenReturn(List.of(record));
    var result = service.build(9, TODAY);
    assertEquals(300, result.tasks().get(0).description().length());
    assertEquals(300, result.recentRecords().get(0).feedback().length());
    verify(mapper).tasks(9, TODAY, TODAY.atStartOfDay());
    verify(mapper).records(9);
  }

  @Test
  void candidateDateBoundaryIncludesNullEqualAndPastButExcludesFuture() {
    var noDate = task(1, null);
    var equal = task(2, TODAY);
    var past = task(3, TODAY.minusDays(1));
    var future = task(4, TODAY.plusDays(1));
    when(mapper.subjects(9)).thenReturn(List.of());
    when(mapper.goals(9)).thenReturn(List.of());
    when(mapper.tasks(9, TODAY, TODAY.atStartOfDay()))
        .thenReturn(List.of(noDate, equal, past, future));
    when(mapper.summary(eq(9L), any(), any()))
        .thenReturn(new AiPlanningContext.StudySummaryContext(0, 0, 0));
    when(mapper.subjectStudy(eq(9L), any(), any())).thenReturn(List.of());
    when(mapper.records(9)).thenReturn(List.of());
    var ids =
        service.build(9, TODAY).tasks().stream().map(AiPlanningContext.TaskContext::id).toList();
    assertEquals(List.of(1L, 2L, 3L), ids);
  }

  private AiPlanningContext.TaskContext task(long id, LocalDate plannedDate) {
    return new AiPlanningContext.TaskContext(
        id,
        2L,
        "Java",
        "#fff",
        null,
        null,
        "任务" + id,
        null,
        3,
        TaskStatus.TODO,
        60,
        plannedDate,
        null);
  }
}
