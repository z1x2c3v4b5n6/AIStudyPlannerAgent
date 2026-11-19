package com.yhk.aistudyplanner.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeepSeekAgentGatewayTest {
    @Test
    void sendsNativeFunctionDefinitionThenReturnsToolResultWithMatchingCallId() {
        var rest = RestClient.builder();
        var server = MockRestServiceServer.bindTo(rest).build();
        var model = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().baseUrl("https://local-fixture.invalid")
                        .completionsPath("/chat/completions").apiKey("offline").restClientBuilder(rest).build())
                .defaultOptions(OpenAiChatOptions.builder().model("deepseek-v4-flash").build()).build();
        var gateway = new DeepSeekAgentGateway(model);
        var callback = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("today_tasks").description("List today's tasks")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
            }
            @Override public String call(String ignored) { fail("Spring AI must not execute callbacks"); return ""; }
        };
        server.expect(requestTo("https://local-fixture.invalid/chat/completions"))
                .andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.tools[0].function.name").value("today_tasks"))
                .andRespond(withSuccess("""
                    {"id":"first","object":"chat.completion","created":1,"model":"deepseek-v4-flash",
                     "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                     "tool_calls":[{"id":"call-1","type":"function","function":{"name":"today_tasks","arguments":"{}"}}]}}]}
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://local-fixture.invalid/chat/completions"))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].id").value("call-1"))
                .andExpect(jsonPath("$.messages[2].tool_call_id").value("call-1"))
                .andExpect(jsonPath("$.messages[2].role").value("tool"))
                .andRespond(withSuccess("""
                    {"id":"second","object":"chat.completion","created":1,"model":"deepseek-v4-flash",
                     "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"无待办"}}]}
                    """, MediaType.APPLICATION_JSON));
        var user = new UserMessage("现在有什么任务");
        var first = gateway.respond(List.of(user), List.of(callback));
        assertEquals("today_tasks", first.getToolCalls().get(0).name());
        var result = ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse("call-1", "today_tasks", "[]"))).build();
        assertEquals("无待办", gateway.respond(List.of(user, first, result), List.of(callback)).getText());
        server.verify();
    }
}
