package com.yhk.aistudyplanner.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.yhk.aistudyplanner.ai.gateway.DeepSeekPlanningGateway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiChatConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(AiChatConfiguration.class);

  @Test
  void applicationContextStartsWithoutApiKeyWhenAiIsDisabled() {
    contextRunner
        .withPropertyValues("app.ai.enabled=false", "app.ai.api-key=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ChatClient.class);
            });
  }

  @Test
  void applicationContextStartsWithoutApiKeyWhenAiWasEnabled() {
    contextRunner
        .withPropertyValues("app.ai.enabled=true", "app.ai.api-key=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ChatClient.class);
            });
  }

  @Test
  void applicationContextStartsAndCreatesManualDeepSeekClientWhenEnabledWithApiKey() {
    contextRunner
        .withPropertyValues("app.ai.enabled=true", "app.ai.api-key=test-deepseek-key")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(ChatClient.class);
              assertThat(context).hasSingleBean(DeepSeekPlanningGateway.class);
            });
  }

  @Test
  void chatOptionsDisableThinkingAndKeepJsonModeSettings() {
    AiPlanningProperties properties = new AiPlanningProperties();
    properties.setModel("deepseek-v4-flash");
    properties.setTemperature(0.2);
    properties.setMaxTokens(2500);

    OpenAiChatOptions options = AiChatConfiguration.buildChatOptions(properties);

    assertThat(options.getModel()).isEqualTo("deepseek-v4-flash");
    assertThat(options.getTemperature()).isEqualTo(0.2);
    assertThat(options.getMaxTokens()).isEqualTo(2500);
    assertThat(options.getResponseFormat()).isNotNull();
    assertThat(options.getExtraBody())
        .containsEntry("thinking", java.util.Map.of("type", "disabled"));
  }
}
