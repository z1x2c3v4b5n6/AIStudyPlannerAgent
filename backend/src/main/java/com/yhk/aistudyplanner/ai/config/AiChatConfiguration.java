package com.yhk.aistudyplanner.ai.config;
import com.yhk.aistudyplanner.ai.gateway.*;import org.springframework.ai.chat.client.ChatClient;import org.springframework.ai.openai.*;import org.springframework.ai.openai.api.*;import org.springframework.boot.autoconfigure.condition.*;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.springframework.context.annotation.*;import org.springframework.http.client.SimpleClientHttpRequestFactory;import org.springframework.web.client.RestClient;import java.time.Duration;

@Configuration @EnableConfigurationProperties(AiPlanningProperties.class)
public class AiChatConfiguration {
 @Bean @ConditionalOnProperty(prefix="app.ai",name="enabled",havingValue="true") @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.ai.api-key:}')")
 ChatClient deepSeekChatClient(AiPlanningProperties p){
  var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Duration.ofSeconds(p.getTimeoutSeconds()));factory.setReadTimeout(Duration.ofSeconds(p.getTimeoutSeconds()));
  var api=OpenAiApi.builder().baseUrl(p.getBaseUrl()).completionsPath(p.getCompletionsPath()).apiKey(p.getApiKey()).restClientBuilder(RestClient.builder().requestFactory(factory)).build();
  var format=ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build();
  var options=OpenAiChatOptions.builder().model(p.getModel()).temperature(p.getTemperature()).maxTokens(p.getMaxTokens()).responseFormat(format).build();
  return ChatClient.create(OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build());
 }
 @Bean @ConditionalOnBean(name="deepSeekChatClient") DeepSeekPlanningGateway deepSeekPlanningGateway(ChatClient deepSeekChatClient){return new DeepSeekPlanningGateway(deepSeekChatClient);}
}
