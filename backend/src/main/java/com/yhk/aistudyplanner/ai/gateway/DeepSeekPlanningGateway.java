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
    try {
      return client.prompt().system(system).user(user).call().content();
    } catch (Exception ex) {
      Throwable root = root(ex);
      if (root instanceof SocketTimeoutException || root instanceof TimeoutException)
        return fail(ErrorCode.AI_PROVIDER_TIMEOUT);
      if (ex instanceof HttpClientErrorException.TooManyRequests)
        return fail(ErrorCode.AI_PROVIDER_RATE_LIMITED);
      throw new AiProviderException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }
  }

  private String fail(ErrorCode code) {
    throw new AiProviderException(code);
  }

  private Throwable root(Throwable e) {
    while (e.getCause() != null) e = e.getCause();
    return e;
  }
}
