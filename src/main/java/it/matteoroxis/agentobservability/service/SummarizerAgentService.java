package it.matteoroxis.agentobservability.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import it.matteoroxis.agentobservability.domain.AgentContext;
import it.matteoroxis.agentobservability.domain.PolicyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.stereotype.Service;

/**
 * Summarizer Agent: calls Claude Haiku 4.5 to compose the final user-facing reply
 * from the policy decision and the retrieved context.
 *
 * The span captures the handoff reason and the tool name so that the
 * orchestrator's routing decisions are visible in the trace.
 */
@Service
public class SummarizerAgentService {

    private static final Logger log = LoggerFactory.getLogger(SummarizerAgentService.class);

    private static final String SYSTEM_PROMPT = """
            You are a customer service assistant. Using the provided context and policy decision,
            compose a helpful, professional, and concise response to the user's query.
            Base your answer only on the provided context. Do not invent information.
            """;

    private final Tracer tracer;
    private final ChatClient chatClient;

    public SummarizerAgentService(Tracer tracer, ChatClient.Builder chatClientBuilder) {
        this.tracer = tracer;
        this.chatClient = chatClientBuilder.build();
    }

    public String summarize(AgentContext context, PolicyResult policyResult) {
        Span summarizerSpan = tracer.spanBuilder("chat summarizer-agent")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.system", "anthropic")
                .setAttribute("gen_ai.request.model", "claude-haiku-4-5")
                .setAttribute("gen_ai.request.temperature", 0.7)
                .setAttribute("agent.name", "summarizer-agent")
                .startSpan();

        try (Scope scope = summarizerSpan.makeCurrent()) {
            log.info("Summarizer agent composing reply (policy decision: {})", policyResult.decision());

            String userMessage = """
                    User query: %s
                    Policy decision: %s (version: %s)
                    Relevant context:
                    %s
                    """.formatted(
                    context.userQuery(),
                    policyResult.decision(),
                    policyResult.policyVersion(),
                    String.join("\n", context.retrievedContext().snippets()));

            ChatResponse chatResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .options(AnthropicChatOptions.builder()
                            .model("claude-haiku-4-5")
                            .temperature(0.7)
                            .build())
                    .call()
                    .chatResponse();

            String answer = chatResponse.getResult().getOutput().getText();
            var usage = chatResponse.getMetadata().getUsage();
            int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            int completionTokens = usage.getGenerationTokens() != null ? usage.getGenerationTokens() : 0;

            summarizerSpan.setAttribute("gen_ai.usage.input_tokens", promptTokens);
            summarizerSpan.setAttribute("gen_ai.usage.output_tokens", completionTokens);
            summarizerSpan.setAttribute("agent.handoff.reason", "policy_evaluated");
            summarizerSpan.setAttribute("agent.tool.name", "compose_reply");

            log.info("Summarizer agent produced reply ({} input / {} output tokens)",
                    promptTokens, completionTokens);
            return answer;

        } catch (Exception e) {
            summarizerSpan.recordException(e);
            summarizerSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            summarizerSpan.end();
        }
    }
}
