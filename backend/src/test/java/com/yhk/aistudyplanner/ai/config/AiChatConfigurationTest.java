package com.yhk.aistudyplanner.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiChatConfiguration.class);

    @Test
    void applicationContextStartsWithoutApiKeyWhenAiIsDisabled() {
        contextRunner
                .withPropertyValues("app.ai.enabled=false", "app.ai.api-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatClient.class);
                });
    }

    @Test
    void applicationContextStartsWithoutApiKeyWhenAiWasEnabled() {
        contextRunner
                .withPropertyValues("app.ai.enabled=true", "app.ai.api-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatClient.class);
                });
    }
}
