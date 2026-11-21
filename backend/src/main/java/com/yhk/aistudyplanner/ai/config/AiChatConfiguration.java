package com.yhk.aistudyplanner.ai.config;

import com.yhk.aistudyplanner.ai.gateway.*;
import com.yhk.aistudyplanner.agent.AgentGateway;
import com.yhk.aistudyplanner.agent.DeepSeekAgentGateway;
import java.time.Duration;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiPlanningProperties.class)
public class AiChatConfiguration {
  @Bean
  @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
  @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.ai.api-key:}')")
  ChatClient deepSeekChatClient(AiPlanningProperties p) {
    return ChatClient.create(OpenAiChatModel.builder()
        .openAiApi(api(p, p.getTimeoutSeconds())).defaultOptions(buildChatOptions(p)).build());
  }

  @Bean
  @ConditionalOnBean(name = "deepSeekChatClient")
  AgentGateway deepSeekAgentGateway(AiPlanningProperties p) {
    var options = OpenAiChatOptions.builder().model(p.getModel()).temperature(p.getTemperature())
        .maxTokens(Math.min(1200, p.getMaxTokens())).internalToolExecutionEnabled(false).build();
    return new DeepSeekAgentGateway(OpenAiChatModel.builder()
        .openAiApi(api(p, Math.min(12, p.getTimeoutSeconds()))).defaultOptions(options).build());
  }

  private OpenAiApi api(AiPlanningProperties p, int timeoutSeconds) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    return OpenAiApi.builder()
            .baseUrl(p.getBaseUrl())
            .completionsPath(p.getCompletionsPath())
            .apiKey(p.getApiKey())
            .restClientBuilder(RestClient.builder().requestFactory(factory))
            .build();
  }

  static OpenAiChatOptions buildChatOptions(AiPlanningProperties p) {
    var format = ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build();
    return OpenAiChatOptions.builder()
        .model(p.getModel())
        .temperature(p.getTemperature())
        .maxTokens(p.getMaxTokens())
        .responseFormat(format)
        .extraBody(Map.of("thinking", Map.of("type", "disabled")))
        .build();
  }

  @Bean
  @ConditionalOnBean(name = "deepSeekChatClient")
  DeepSeekPlanningGateway deepSeekPlanningGateway(ChatClient deepSeekChatClient, AiPlanningProperties properties) {
    var bounded = ChatClient.create(OpenAiChatModel.builder()
        .openAiApi(api(properties, Math.min(15, properties.getTimeoutSeconds())))
        .defaultOptions(buildChatOptions(properties))
        .retryTemplate(new org.springframework.retry.support.RetryTemplateBuilder().maxAttempts(1).build())
        .build());
    return new DeepSeekPlanningGateway(deepSeekChatClient,bounded,properties.getMaxTokens());
  }
}
