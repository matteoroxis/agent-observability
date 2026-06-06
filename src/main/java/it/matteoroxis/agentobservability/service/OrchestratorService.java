package it.matteoroxis.agentobservability.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import it.matteoroxis.agentobservability.domain.AgentContext;
import it.matteoroxis.agentobservability.domain.PolicyResult;
import it.matteoroxis.agentobservability.domain.RetrievedContext;
import it.matteoroxis.agentobservability.dto.response.OrchestrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full multi-agent pipeline:
 *  1. Retrieval Agent  — fetches relevant documents
 *  2. Policy Agent     — evaluates compliance
 *  3. Summarizer Agent — composes the user-facing reply
 *
 * The orchestrator span becomes the parent of every agent span because each
 * child span is created while this span's Scope is active.  OpenTelemetry
 * propagates the context automatically through the thread-local Scope, so no
 * manual parent linking is required.
 *
 * The resulting trace in Jaeger shows one tree:
 *   agent.orchestrator
 *     └── chat retrieval-agent
 *     └── chat policy-agent
 *     └── chat summarizer-agent
 */
@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final Tracer tracer;
    private final RetrievalAgentService retrievalAgent;
    private final PolicyAgentService policyAgent;
    private final SummarizerAgentService summarizerAgent;

    public OrchestratorService(Tracer tracer,
                               RetrievalAgentService retrievalAgent,
                               PolicyAgentService policyAgent,
                               SummarizerAgentService summarizerAgent) {
        this.tracer = tracer;
        this.retrievalAgent = retrievalAgent;
        this.policyAgent = policyAgent;
        this.summarizerAgent = summarizerAgent;
    }

    public OrchestrationResponse orchestrate(String userQuery) {
        Span orchestratorSpan = tracer.spanBuilder("agent.orchestrator")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("agent.name", "orchestrator")
                .setAttribute("agent.handoff.reason", "user_request")
                .startSpan();

        try (Scope scope = orchestratorSpan.makeCurrent()) {
            log.info("Orchestrator starting for query: {}", userQuery);

            // Step 1: retrieve relevant context — child span auto-nested here
            RetrievedContext retrievedContext = retrievalAgent.retrieve(userQuery);

            // Step 2: evaluate policy against the retrieved context
            AgentContext context = new AgentContext(userQuery, retrievedContext);
            PolicyResult policyResult = policyAgent.evaluate(context);

            // Step 3: compose the final user-facing reply
            String answer = summarizerAgent.summarize(context, policyResult);

            int totalInputTokens = retrievedContext.promptTokens()
                    + policyResult.promptTokens()
                    + policyResult.completionTokens();

            // Aggregate cost visible at the root span — no more hidden token spend
            orchestratorSpan.setAttribute("agent.orchestrator.steps_completed", 3);
            orchestratorSpan.setAttribute("agent.orchestrator.total_input_tokens", totalInputTokens);

            log.info("Orchestrator completed (total input tokens: {})", totalInputTokens);
            return new OrchestrationResponse(answer, policyResult.policyVersion(), policyResult.sourceDocIds());

        } catch (Exception e) {
            orchestratorSpan.recordException(e);
            orchestratorSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            orchestratorSpan.end();
        }
    }
}
