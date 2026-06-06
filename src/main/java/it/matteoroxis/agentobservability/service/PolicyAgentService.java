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
 * Policy Agent: calls Claude Haiku 4.5 to evaluate compliance of the user request
 * against company policies and returns an APPROVED/DENIED decision.
 *
 * Token counts and the policy version are recorded as span attributes so that
 * a single trace shows exactly which policy version answered the request and
 * how much it cost — solving the Cost Problem described in the article.
 */
@Service
public class PolicyAgentService {

    private static final Logger log = LoggerFactory.getLogger(PolicyAgentService.class);

    private static final String POLICY_VERSION = "policy-v3.2";

    private static final String SYSTEM_PROMPT = """
            You are a policy compliance agent for a customer service system.
            Review the user query and the retrieved knowledge base context,
            then decide whether the request complies with company policies.
            Reply with exactly one word: APPROVED or DENIED.
            """;

    private final Tracer tracer;
    private final ChatClient chatClient;

    public PolicyAgentService(Tracer tracer, ChatClient.Builder chatClientBuilder) {
        this.tracer = tracer;
        this.chatClient = chatClientBuilder.build();
    }

    public PolicyResult evaluate(AgentContext context) {
        Span policySpan = tracer.spanBuilder("chat policy-agent")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.system", "anthropic")
                .setAttribute("gen_ai.request.model", "claude-haiku-4-5")
                .setAttribute("gen_ai.request.temperature", 0.2)
                .setAttribute("agent.name", "policy-agent")
                .startSpan();

        try (Scope scope = policySpan.makeCurrent()) {
            log.info("Policy agent evaluating context with {} documents",
                    context.retrievedContext().docIds().size());

            String userMessage = """
                    User query: %s
                    Retrieved context: %s
                    """.formatted(
                    context.userQuery(),
                    String.join("\n", context.retrievedContext().snippets()));

            ChatResponse chatResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .options(AnthropicChatOptions.builder()
                            .model("claude-haiku-4-5")
                            .temperature(0.2)
                            .build())
                    .call()
                    .chatResponse();

            String rawDecision = chatResponse.getResult().getOutput().getText().trim().toUpperCase();
            String decision = rawDecision.startsWith("DENIED") ? "DENIED" : "APPROVED";

            var usage = chatResponse.getMetadata().getUsage();
            int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            int completionTokens = usage.getGenerationTokens() != null ? usage.getGenerationTokens() : 0;

            policySpan.setAttribute("gen_ai.usage.input_tokens", promptTokens);
            policySpan.setAttribute("gen_ai.usage.output_tokens", completionTokens);
            policySpan.setAttribute("agent.policy.version", POLICY_VERSION);
            policySpan.setAttribute("agent.policy.decision", decision);
            policySpan.setAttribute("agent.retrieval.doc_ids",
                    String.join(",", context.retrievedContext().docIds()));

            log.info("Policy agent decision: {} (policy {}, {} input / {} output tokens)",
                    decision, POLICY_VERSION, promptTokens, completionTokens);
            return new PolicyResult(
                    decision,
                    POLICY_VERSION,
                    context.retrievedContext().docIds(),
                    promptTokens,
                    completionTokens
            );

        } catch (Exception e) {
            policySpan.recordException(e);
            policySpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            policySpan.end();
        }
    }
}
