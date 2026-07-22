package com.yhk.aistudyplanner.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.dto.AiPlanModelResponse;
import com.yhk.aistudyplanner.ai.gateway.AiFailureCategory;
import com.yhk.aistudyplanner.ai.gateway.AiProviderException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class AiPlanResponseParser {
  private final ObjectMapper mapper;

  public AiPlanResponseParser(ObjectMapper m) {
    mapper = m;
  }

  public AiPlanModelResponse parse(String content) {
    if (content == null || content.isBlank())
      throw new AiProviderException(ErrorCode.AI_RESPONSE_EMPTY);
    String json = content.trim();
    if (json.startsWith("```")) {
      json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
    int start = json.indexOf('{'), end = json.lastIndexOf('}');
    if (start < 0 || end < start)
      throw new AiProviderException(
          ErrorCode.AI_RESPONSE_INVALID, AiFailureCategory.JSON_PARSE_FAILED);
    try {
      return mapper.readValue(json.substring(start, end + 1), AiPlanModelResponse.class);
    } catch (Exception e) {
      throw new AiProviderException(
          ErrorCode.AI_RESPONSE_INVALID, AiFailureCategory.JSON_PARSE_FAILED);
    }
  }
}
