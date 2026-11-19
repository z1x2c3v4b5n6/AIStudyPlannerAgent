package com.yhk.aistudyplanner.learningpath.service;

import com.yhk.aistudyplanner.ai.config.AiChatConfiguration;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.gateway.AiProviderException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Explicit, test-only provider. Uses production DeepSeek chat configuration and records usage. */
final class GenerationLiveGateway implements AutoCloseable {
    private final AnnotationConfigApplicationContext context;
    private final ChatClient client;
    private final AiPlanningProperties settings;
    private int calls;
    private int responsesWithUsage;
    private long inputTokens;
    private long outputTokens;

    private GenerationLiveGateway(AnnotationConfigApplicationContext context) {
        this.context = context;
        client = context.getBean(ChatClient.class);
        settings = context.getBean(AiPlanningProperties.class);
    }

    static GenerationLiveGateway open() {
        if (System.getenv("CI") != null || !"true".equals(System.getenv("GENERATION_EVAL_ALLOW_PAID"))) {
            throw new IllegalStateException("Live evaluation requires local GENERATION_EVAL_ALLOW_PAID=true");
        }
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set DEEPSEEK_API_KEY");
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("evaluation", Map.of(
                "app.ai.enabled", "true", "app.ai.api-key", key,
                "app.ai.base-url", "https://api.deepseek.com", "app.ai.completions-path", "/chat/completions",
                "app.ai.model", System.getenv().getOrDefault("GENERATION_EVAL_MODEL", "deepseek-v4-flash"),
                "app.ai.temperature", "0.2", "app.ai.max-tokens", "2500")));
        try {
            context.register(AiChatConfiguration.class);
            context.refresh();
            return new GenerationLiveGateway(context);
        } catch (RuntimeException ex) {
            context.close();
            throw ex;
        }
    }

    String generate(String system, String user) {
        calls++;
        try {
            var response = client.prompt().system(system).user(user).call().chatResponse();
            if (response == null) return "";
            var usage = response.getMetadata().getUsage();
            if (usage != null && usage.getPromptTokens() != null && usage.getCompletionTokens() != null) {
                responsesWithUsage++;
                inputTokens += usage.getPromptTokens();
                outputTokens += usage.getCompletionTokens();
            }
            return response.getResult().getOutput().getText();
        } catch (RuntimeException ex) {
            throw new AiProviderException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        }
    }

    Map<String, Object> metadata() {
        var result = new LinkedHashMap<String, Object>();
        result.put("model", settings.getModel());
        result.put("temperature", settings.getTemperature());
        result.put("maxOutputTokens", settings.getMaxTokens());
        result.put("gatewayCalls", calls);
        result.put("responsesWithUsage", responsesWithUsage);
        result.put("reportedInputTokens", inputTokens);
        result.put("reportedOutputTokens", outputTokens);
        return result;
    }

    @Override
    public void close() {
        context.close();
    }
}
