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
 * Simulates a Summarizer Agent that composes the final user-facing reply
 * from the policy decision and the retrieved context.
 *
 * The span captures the handoff reason and the tool name so that the
 * orchestrator's routing decisions are visible in the trace.
 */
@Service
public class SummarizerAgentService {

    private static final Logger log = LoggerFactory.getLogger(SummarizerAgentService.class);

    private final Tracer tracer;

    public SummarizerAgentService(Tracer tracer) {
        this.tracer = tracer;
    }

    public String summarize(AgentContext context, PolicyResult policyResult) {
        Span summarizerSpan = tracer.spanBuilder("chat summarizer-agent")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.system", "openai")
                .setAttribute("gen_ai.request.model", "gpt-4o")
                .setAttribute("gen_ai.request.temperature", 0.7)
                .setAttribute("agent.name", "summarizer-agent")
                .startSpan();

        try (Scope scope = summarizerSpan.makeCurrent()) {
            log.info("Summarizer agent composing reply (policy decision: {})", policyResult.decision());

            // Simulated LLM call: compose a user-facing answer from all prior context
            int promptTokens = 1200 + policyResult.promptTokens() + policyResult.completionTokens();
            int completionTokens = 320;

            String answer = "Based on our policy (%s), your request has been %s. ".formatted(
                    policyResult.policyVersion(), policyResult.decision().toLowerCase())
                    + "We reviewed %d relevant documents to provide this answer. ".formatted(
                    context.retrievedContext().docIds().size())
                    + "Query: " + context.userQuery();

            summarizerSpan.setAttribute("gen_ai.usage.input_tokens", promptTokens);
            summarizerSpan.setAttribute("gen_ai.usage.output_tokens", completionTokens);
            summarizerSpan.setAttribute("agent.handoff.reason", "policy_evaluated");
            summarizerSpan.setAttribute("agent.tool.name", "compose_reply");

            log.info("Summarizer agent produced reply ({} output tokens)", completionTokens);
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
