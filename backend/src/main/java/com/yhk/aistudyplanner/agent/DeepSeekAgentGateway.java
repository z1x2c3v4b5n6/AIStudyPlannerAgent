package com.yhk.aistudyplanner.agent;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

public record DeepSeekAgentGateway(OpenAiChatModel model) implements AgentGateway {
    @Override
    public AssistantMessage respond(List<Message> messages, List<ToolCallback> tools) {
        try {
            var options = OpenAiChatOptions.builder().toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .extraBody(Map.of("thinking", Map.of("type", "disabled"))).build();
            var response = model.call(new Prompt(messages, options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_EMPTY);
            }
            return response.getResult().getOutput();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (ex instanceof org.springframework.web.client.HttpClientErrorException.TooManyRequests) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_RATE_LIMITED);
            }
            if (ex instanceof org.springframework.web.client.ResourceAccessException
                    && ex.getCause() instanceof java.net.SocketTimeoutException) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_TIMEOUT);
            }
            throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        }
    }
}
