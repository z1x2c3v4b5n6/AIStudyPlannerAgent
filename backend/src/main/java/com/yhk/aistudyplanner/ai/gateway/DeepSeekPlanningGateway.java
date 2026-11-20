package com.yhk.aistudyplanner.ai.gateway;

import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.net.*;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.*;

public class DeepSeekPlanningGateway implements PlanningGateway {
  private final ChatClient client;

  public DeepSeekPlanningGateway(ChatClient client) {
    this.client = client;
  }

  public String generate(String system, String user) {
    return generateWithUsage(system,user).text();
  }

  @Override
  public Completion generateWithUsage(String system, String user) {
    try {
      var response=client.prompt().system(system).user(user).call().chatResponse();
      if(response==null || response.getResult()==null) return new Completion(null,null,null);
      var usage=response.getMetadata().getUsage();
      return new Completion(response.getResult().getOutput().getText(),
          usage==null?null:usage.getPromptTokens(),usage==null?null:usage.getCompletionTokens());
    } catch (Exception ex) {
      Throwable root = root(ex);
      if (root instanceof SocketTimeoutException || root instanceof TimeoutException)
        throw new AiProviderException(ErrorCode.AI_PROVIDER_TIMEOUT);
      if (ex instanceof HttpClientErrorException.TooManyRequests)
        throw new AiProviderException(ErrorCode.AI_PROVIDER_RATE_LIMITED);
      throw new AiProviderException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }
  }

  private Throwable root(Throwable e) {
    while (e.getCause() != null) e = e.getCause();
    return e;
  }
}
