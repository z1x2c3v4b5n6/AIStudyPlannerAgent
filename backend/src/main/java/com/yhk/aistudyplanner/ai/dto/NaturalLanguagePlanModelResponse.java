package com.yhk.aistudyplanner.ai.dto;

import java.util.List;

public record NaturalLanguagePlanModelResponse(
    String planDate,
    String startTime,
    Integer availableMinutes,
    String requirement,
    List<String> preferences,
    List<String> topicKeywords,
    Boolean needsClarification,
    String clarificationMessage) {}
