package com.yhk.aistudyplanner.agent;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

public interface AgentGateway {
    AssistantMessage respond(List<Message> messages, List<ToolCallback> tools);
}
