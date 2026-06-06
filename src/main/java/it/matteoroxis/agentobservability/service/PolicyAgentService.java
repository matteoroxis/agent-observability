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
import org.springframework.stereotype.Service;

/**
 * Simulates a Policy Agent that evaluates compliance rules against the retrieved
 * context and produces an approval decision.
 *
 * Token usage and the policy version are recorded as span attributes so that
 * a single trace shows exactly which policy version answered the request and
 * how much it cost — solving the Cost Problem described in the article.
 */
@Service
public class PolicyAgentService {

    private static final Logger log = LoggerFactory.getLogger(PolicyAgentService.class);

    private final Tracer tracer;

    public PolicyAgentService(Tracer tracer) {
        this.tracer = tracer;
    }

    public PolicyResult evaluate(AgentContext context) {
        Span policySpan = tracer.spanBuilder("chat policy-agent")
                .setSpanKind(SpanKind.CLIENT)
                // GenAI semantic convention attributes
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.system", "openai")
                .setAttribute("gen_ai.request.model", "gpt-4o")
                .setAttribute("gen_ai.request.temperature", 0.2)
                .setAttribute("agent.name", "policy-agent")
                .startSpan();

        try (Scope scope = policySpan.makeCurrent()) {
            log.info("Policy agent evaluating context with {} documents",
                    context.retrievedContext().docIds().size());

            // Simulated LLM call: evaluate policy against retrieved context
            String decision = "APPROVED";
            String policyVersion = "policy-v3.2";
            int promptTokens = 820 + context.retrievedContext().promptTokens();
            int completionTokens = 180;

            // Record token cost and causality attributes after the call
            policySpan.setAttribute("gen_ai.usage.input_tokens", promptTokens);
            policySpan.setAttribute("gen_ai.usage.output_tokens", completionTokens);
            policySpan.setAttribute("agent.policy.version", policyVersion);
            policySpan.setAttribute("agent.policy.decision", decision);
            policySpan.setAttribute("agent.retrieval.doc_ids",
                    String.join(",", context.retrievedContext().docIds()));

            log.info("Policy agent decision: {} (policy {})", decision, policyVersion);
            return new PolicyResult(
                    decision,
                    policyVersion,
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
