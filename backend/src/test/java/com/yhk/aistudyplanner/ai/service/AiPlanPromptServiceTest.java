package com.yhk.aistudyplanner.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.vo.AiPlanningContext;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class AiPlanPromptServiceTest {

  @Test
  void userControlledRequirementCannotReplaceTemplatePlaceholders() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    AiPlanPromptService service = new AiPlanPromptService(new DefaultResourceLoader(), mapper);
    String requirement = "保留<contextJson>\n并包含\"引号\"和<payloadJson>";
    PlanDraftRequest request =
        new PlanDraftRequest(LocalDate.of(2026, 7, 23), LocalTime.of(9, 0), 180, requirement);
    AiPlanningContext context =
        new AiPlanningContext(
            List.of(),
            List.of(),
            List.of(),
            new AiPlanningContext.StudySummaryContext(0, 0, 0),
            List.of(),
            List.of());

    String prompt = service.user(request, context);
    String payload = prompt.substring(prompt.indexOf('：') + 1).trim();
    var json = mapper.readTree(payload);

    assertEquals(requirement, json.get("requirement").asText());
    assertEquals(180, json.get("availableMinutes").asInt());
    assertTrue(json.get("context").get("tasks").isArray());
  }
}
