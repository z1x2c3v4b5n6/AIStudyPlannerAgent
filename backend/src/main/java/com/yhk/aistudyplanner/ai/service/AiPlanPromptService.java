package com.yhk.aistudyplanner.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.vo.AiPlanningContext;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;

@Service
public class AiPlanPromptService {
  private final String systemTemplate, userTemplate;
  private final ObjectMapper objectMapper;

  public AiPlanPromptService(ResourceLoader loader, ObjectMapper mapper) throws IOException {
    objectMapper = mapper;
    systemTemplate = read(loader, "classpath:prompts/ai-plan-system.st");
    userTemplate = read(loader, "classpath:prompts/ai-plan-user.st");
  }

  public String system() {
    return systemTemplate;
  }

  public String user(PlanDraftRequest r, AiPlanningContext c) {
    try {
      var payload =
          new PromptPayload(r.planDate(), r.startTime(), r.availableMinutes(), r.requirement(), c);
      return userTemplate.replace("<payloadJson>", objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("AI context serialization failed");
    }
  }

  private String read(ResourceLoader l, String p) throws IOException {
    try (var in = l.getResource(p).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private record PromptPayload(
      java.time.LocalDate planDate,
      java.time.LocalTime startTime,
      Integer availableMinutes,
      String requirement,
      AiPlanningContext context) {}
}
