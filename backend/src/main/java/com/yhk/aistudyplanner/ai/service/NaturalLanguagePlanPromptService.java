package com.yhk.aistudyplanner.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.dto.NaturalLanguagePlanParseRequest;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class NaturalLanguagePlanPromptService {
  private static final String SYSTEM_PROMPT =
      """
      你是学习计划需求解析器。只提取用户明确表达的信息，不制定计划，不创建任务。
      所有相对日期都以北京时间和输入的today为基准。
      必须只返回一个JSON对象，不要Markdown或解释。字段：
      planDate(yyyy-MM-dd或null)、startTime(HH:mm:ss或null)、
      availableMinutes(正整数或null)、requirement(保留学习重点和顺序要求的简洁中文)、
      preferences(字符串数组，包含节奏、强度等偏好)、
      topicKeywords(字符串数组，只包含用户明确提到的学习主题、技术或科目名称)、
      needsClarification(boolean)、clarificationMessage(string或null)。
      无法确定日期、开始时间或时长时必须设置needsClarification=true，并明确说明缺少什么；
      不得自行猜测用户没有表达的信息。
      """;

  private final ObjectMapper objectMapper;

  public NaturalLanguagePlanPromptService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String system() {
    return SYSTEM_PROMPT;
  }

  public String user(
      NaturalLanguagePlanParseRequest request, LocalDate today, String zoneId) {
    try {
      return objectMapper.writeValueAsString(
          new PromptPayload(today, zoneId, request.text()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Natural language request serialization failed");
    }
  }

  private record PromptPayload(LocalDate today, String timeZone, String userInput) {}
}
