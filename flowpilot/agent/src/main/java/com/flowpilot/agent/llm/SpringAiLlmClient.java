package com.flowpilot.agent.llm;

import com.flowpilot.agent.context.AgentContext;
import com.flowpilot.agent.prompt.PromptTemplateManager;
import com.flowpilot.agent.tool.ToolRegistry;
import com.flowpilot.common.util.Json;
import java.time.Duration;
import java.util.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SpringAiLlmClient {
  public record Config(
      String url,
      String path,
      String key,
      String model,
      Double temperature,
      int maxOutput,
      String outputParameter,
      int timeout) {}

  public record Result(LlmClient.Reply reply, Long input, Long output, Long total) {}

  private final ToolRegistry registry;
  private final PromptTemplateManager prompts;

  public SpringAiLlmClient(ToolRegistry registry, PromptTemplateManager prompts) {
    this.registry = registry;
    this.prompts = prompts;
  }

  public List<Message> messages(AgentContext c) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(prompts.system(c)));
    messages.add(new UserMessage(c.body()));
    for (var t : c.history()) {
      if (t.role().equals("assistant")) {
        messages.add(
            AssistantMessage.builder()
                .content(t.text() == null ? "" : t.text())
                .toolCalls(
                    t.calls().stream()
                        .map(
                            a ->
                                new AssistantMessage.ToolCall(
                                    a.id(), "function", a.name(), a.arguments()))
                        .toList())
                .build());
      } else if (t.role().equals("tool")) {
        messages.add(
            ToolResponseMessage.builder()
                .responses(
                    List.of(
                        new ToolResponseMessage.ToolResponse(t.callId(), t.toolName(), t.text())))
                .build());
      }
    }
    return messages;
  }

  // Reserve an intentionally conservative UTF-8 byte bound including prompts, history, and tool
  // schemas.
  public long inputBound(AgentContext c) {
    long bytes = 2048;
    for (var m : messages(c))
      bytes += Json.write(m).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 128;
    for (var t : registry.callbacks())
      bytes +=
          Json.write(t.getToolDefinition()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
              + 128;
    return bytes;
  }

  public Result call(AgentContext c, Config config) {
    var f =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    f.setReadTimeout(Duration.ofSeconds(config.timeout()));
    var api =
        OpenAiApi.builder()
            .baseUrl(config.url())
            .completionsPath(config.path())
            .apiKey(config.key())
            .restClientBuilder(RestClient.builder().requestFactory(f))
            .build();
    var options =
        OpenAiChatOptions.builder()
            .model(config.model())
            .toolCallbacks(registry.callbacks())
            .internalToolExecutionEnabled(false);
    if (config.temperature() != null) options.temperature(config.temperature());
    if (config.outputParameter().equals("MAX_COMPLETION_TOKENS"))
      options.maxCompletionTokens(config.maxOutput());
    else options.maxTokens(config.maxOutput());
    var model =
        OpenAiChatModel.builder()
            .openAiApi(api)
            .retryTemplate(
                org.springframework.retry.support.RetryTemplate.builder().maxAttempts(1).build())
            .defaultOptions(options.build())
            .build();
    var response = model.call(new Prompt(messages(c)));
    var output = response.getResult().getOutput();
    var reply =
        new LlmClient.Reply(
            output.getText(),
            output.getToolCalls().stream()
                .map(t -> new AgentContext.Call(t.id(), t.name(), t.arguments()))
                .toList());
    var usage = response.getMetadata().getUsage();
    return new Result(
        reply,
        usage == null || usage.getPromptTokens() == null
            ? null
            : usage.getPromptTokens().longValue(),
        usage == null || usage.getCompletionTokens() == null
            ? null
            : usage.getCompletionTokens().longValue(),
        usage == null || usage.getTotalTokens() == null
            ? null
            : usage.getTotalTokens().longValue());
  }
}
