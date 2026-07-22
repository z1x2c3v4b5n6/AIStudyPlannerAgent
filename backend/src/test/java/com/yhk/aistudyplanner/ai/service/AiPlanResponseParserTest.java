package com.yhk.aistudyplanner.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.gateway.AiFailureCategory;
import com.yhk.aistudyplanner.ai.gateway.AiProviderException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class AiPlanResponseParserTest {
  private final AiPlanResponseParser parser = new AiPlanResponseParser(new ObjectMapper());

  @Test
  void parsesPlainAndMarkdownJson() {
    String json =
        "{\"summary\":\"ok\",\"items\":[{\"taskId\":1,\"plannedMinutes\":30,\"reason\":\"due\"}]}";
    assertEquals(1, parser.parse(json).items().size());
    assertEquals(1, parser.parse("```json\n" + json + "\n```").items().size());
  }

  @Test
  void emptyAndInvalidAreClassified() {
    assertEquals(
        ErrorCode.AI_RESPONSE_EMPTY,
        assertThrows(AiProviderException.class, () -> parser.parse(" ")).code());
    AiProviderException invalid =
        assertThrows(AiProviderException.class, () -> parser.parse("not json"));
    assertEquals(ErrorCode.AI_RESPONSE_INVALID, invalid.code());
    assertEquals(AiFailureCategory.JSON_PARSE_FAILED.name(), invalid.category());
  }
}
