package com.yhk.aistudyplanner.ai.gateway;

public interface PlanningGateway {
  String generate(String systemPrompt, String userPrompt);

  record Completion(String text, Integer promptTokens, Integer completionTokens) {}

  default Completion generateWithUsage(String systemPrompt,String userPrompt) {
    return new Completion(generate(systemPrompt,userPrompt),null,null);
  }
}
