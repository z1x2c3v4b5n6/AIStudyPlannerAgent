package com.yhk.aistudyplanner.ai.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yhk.aistudyplanner.ai.dto.AiPlanModelResponse;
import com.yhk.aistudyplanner.ai.gateway.AiFailureCategory;
import com.yhk.aistudyplanner.ai.gateway.AiProviderException;
import com.yhk.aistudyplanner.ai.vo.AiPlanningContext;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiPlanResponseValidatorTest {
  private static final LocalDate PLAN_DATE = LocalDate.of(2026, 7, 23);
  private final AiPlanResponseValidator validator = new AiPlanResponseValidator();

  @Test
  void plannedMinutesEqualToEstimatedMinutesIsValid() {
    var draft = validate(120, List.of(task(10, 60)), items(item(10, 60)));

    assertEquals(60, draft.plannedMinutes());
    assertEquals(60, draft.items().get(0).plannedMinutes());
  }

  @Test
  void plannedMinutesAboveEstimatedMinutesIsClamped() {
    var draft = validate(120, List.of(task(10, 30)), items(item(10, 45)));

    assertEquals(30, draft.items().get(0).plannedMinutes());
  }

  @Test
  void estimatedMinutesBelowFifteenAllowsFifteenMinutes() {
    var draft = validate(30, List.of(task(10, 10)), items(item(10, 15)));

    assertEquals(15, draft.plannedMinutes());
  }

  @Test
  void shortPositiveDurationIsRaisedToFifteenMinutes() {
    var draft = validate(30, List.of(task(10, 30)), items(item(10, 10)));

    assertEquals(15, draft.items().get(0).plannedMinutes());
  }

  @Test
  void estimateBelowFifteenCapsOversizedDurationAtFifteenMinutes() {
    var draft = validate(30, List.of(task(10, 10)), items(item(10, 30)));

    assertEquals(15, draft.items().get(0).plannedMinutes());
  }

  @Test
  void nullZeroAndNegativeDurationsRemainInvalid() {
    assertInvalidDuration(new AiPlanModelResponse.Item(10L, null, "安排理由"));
    assertInvalidDuration(item(10, 0));
    assertInvalidDuration(item(10, -1));
  }

  @Test
  void normalizedItemsAreTruncatedToAvailableTime() {
    var draft =
        validate(75, List.of(task(10, 60), task(11, 60)), items(item(10, 90), item(11, 90)));

    assertEquals(75, draft.plannedMinutes());
    assertEquals(60, draft.items().get(0).plannedMinutes());
    assertEquals(15, draft.items().get(1).plannedMinutes());
  }

  @Test
  void stopsAddingTasksWhenRemainingTimeIsBelowFifteenMinutes() {
    var draft =
        validate(70, List.of(task(10, 60), task(11, 30)), items(item(10, 60), item(11, 30)));

    assertEquals(60, draft.plannedMinutes());
    assertEquals(1, draft.items().size());
  }

  @Test
  void validationFailuresExposeSafeSpecificCategories() {
    assertCategory(
        AiFailureCategory.TASK_ID_INVALID,
        () -> validate(60, List.of(task(10, 60)), items(item(99, 30))));
    assertCategory(
        AiFailureCategory.TASK_DUPLICATED,
        () -> validate(60, List.of(task(10, 60)), items(item(10, 30), item(10, 30))));
    assertCategory(
        AiFailureCategory.TEXT_INVALID,
        () ->
            validator.validate(
                request(60),
                context(List.of(task(10, 60))),
                new AiPlanModelResponse(" ", List.of(item(10, 30)))));
  }

  private com.yhk.aistudyplanner.plan.vo.PlanDraftView validate(
      int availableMinutes,
      List<AiPlanningContext.TaskContext> tasks,
      AiPlanModelResponse response) {
    return validator.validate(request(availableMinutes), context(tasks), response);
  }

  private PlanDraftRequest request(int availableMinutes) {
    return new PlanDraftRequest(PLAN_DATE, LocalTime.of(9, 0), availableMinutes, null);
  }

  private AiPlanningContext context(List<AiPlanningContext.TaskContext> tasks) {
    return new AiPlanningContext(
        List.of(),
        List.of(),
        tasks,
        new AiPlanningContext.StudySummaryContext(0, 0, 0),
        List.of(),
        List.of());
  }

  private AiPlanningContext.TaskContext task(long id, int estimatedMinutes) {
    return new AiPlanningContext.TaskContext(
        id,
        1L,
        "Java",
        "#409EFF",
        null,
        null,
        "任务" + id,
        null,
        3,
        TaskStatus.TODO,
        estimatedMinutes,
        PLAN_DATE,
        null);
  }

  private AiPlanModelResponse response(String summary, AiPlanModelResponse.Item... items) {
    return new AiPlanModelResponse(summary, List.of(items));
  }

  private AiPlanModelResponse items(AiPlanModelResponse.Item... items) {
    return response("安排说明", items);
  }

  private AiPlanModelResponse.Item item(long taskId, int minutes) {
    return new AiPlanModelResponse.Item(taskId, minutes, "安排理由");
  }

  private void assertCategory(
      AiFailureCategory category, org.junit.jupiter.api.function.Executable action) {
    AiProviderException exception = assertThrows(AiProviderException.class, action);
    assertEquals(category.name(), exception.category());
  }

  private void assertInvalidDuration(AiPlanModelResponse.Item invalidItem) {
    assertCategory(
        AiFailureCategory.ITEM_DURATION_INVALID,
        () -> validate(60, List.of(task(10, 60)), items(invalidItem)));
  }
}
