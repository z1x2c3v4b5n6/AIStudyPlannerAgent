package com.yhk.aistudyplanner.ai.validator;

import com.yhk.aistudyplanner.ai.dto.AiPlanModelResponse;
import com.yhk.aistudyplanner.ai.gateway.AiFailureCategory;
import com.yhk.aistudyplanner.ai.gateway.AiProviderException;
import com.yhk.aistudyplanner.ai.vo.AiPlanningContext;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import com.yhk.aistudyplanner.plan.vo.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AiPlanResponseValidator {
  public PlanDraftView validate(
      PlanDraftRequest request, AiPlanningContext context, AiPlanModelResponse response) {
    return validateWithReport(request, context, response).draft();
  }

  public ValidationResult validateWithReport(
      PlanDraftRequest request, AiPlanningContext context, AiPlanModelResponse response) {
    if (response == null || response.items() == null || response.items().isEmpty())
      invalid(AiFailureCategory.TEXT_INVALID);
    if (response.summary() == null
        || response.summary().isBlank()
        || response.summary().length() > 1000) invalid(AiFailureCategory.TEXT_INVALID);
    Map<Long, AiPlanningContext.TaskContext> candidates =
        context.tasks().stream()
            .collect(Collectors.toMap(AiPlanningContext.TaskContext::id, Function.identity()));
    Set<Long> used = new HashSet<>();
    List<ValidatedItem> validatedItems = new ArrayList<>();
    for (var item : response.items()) {
      if (item == null || item.taskId() == null) invalid(AiFailureCategory.TASK_ID_INVALID);
      if (!used.add(item.taskId())) invalid(AiFailureCategory.TASK_DUPLICATED);
      var task = candidates.get(item.taskId());
      if (task == null) invalid(AiFailureCategory.TASK_ID_INVALID);
      if (item.plannedMinutes() == null || item.plannedMinutes() <= 0)
        invalid(AiFailureCategory.ITEM_DURATION_INVALID);
      if (item.reason() == null || item.reason().isBlank() || item.reason().length() > 500)
        invalid(AiFailureCategory.TEXT_INVALID);
      validatedItems.add(new ValidatedItem(item, task));
    }

    List<PlanDraftItemView> result = new ArrayList<>();
    int remaining = request.availableMinutes();
    int adjustedItems = 0;
    LocalDateTime cursor = LocalDateTime.of(request.planDate(), request.startTime());
    for (var validatedItem : validatedItems) {
      if (remaining < 15) break;
      var item = validatedItem.item();
      var task = validatedItem.task();
      int maxAllowedMinutes = Math.max(15, task.estimatedMinutes());
      int minutes = Math.max(15, Math.min(item.plannedMinutes(), maxAllowedMinutes));
      boolean adjusted = minutes != item.plannedMinutes();
      if (minutes > remaining) {
        minutes = remaining;
        adjusted = true;
      }
      LocalDateTime end = cursor.plusMinutes(minutes);
      if (!end.toLocalDate().equals(request.planDate()))
        invalid(AiFailureCategory.TOTAL_DURATION_INVALID);
      result.add(
          new PlanDraftItemView(
              result.size() + 1,
              task.id(),
              task.title(),
              task.subjectId(),
              task.subjectName(),
              task.subjectColor(),
              cursor,
              end,
              minutes,
              item.reason().trim()));
      if (adjusted) adjustedItems++;
      cursor = end;
      remaining -= minutes;
    }
    if (result.isEmpty()) invalid(AiFailureCategory.TOTAL_DURATION_INVALID);
    int planned = request.availableMinutes() - remaining;
    return new ValidationResult(
        new PlanDraftView(
            UUID.randomUUID().toString(),
            request.planDate(),
            request.startTime(),
            request.availableMinutes(),
            planned,
            trim(request.requirement()),
            response.summary().trim(),
            List.copyOf(result)),
        adjustedItems);
  }

  private void invalid(AiFailureCategory category) {
    throw new AiProviderException(ErrorCode.AI_RESPONSE_INVALID, category);
  }

  private String trim(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  public record ValidationResult(PlanDraftView draft, int adjustedItems) {}

  private record ValidatedItem(AiPlanModelResponse.Item item, AiPlanningContext.TaskContext task) {}
}
