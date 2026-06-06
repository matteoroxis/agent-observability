package it.matteoroxis.agentobservability.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import it.matteoroxis.agentobservability.domain.RetrievedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Simulates a Retrieval Agent that fetches relevant documents from a vector store
 * and synthesizes them via an LLM call.
 *
 * Each invocation creates a child OTel span following the GenAI semantic conventions
 * (https://opentelemetry.io/docs/specs/semconv/gen-ai/).
 */
@Service
public class RetrievalAgentService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalAgentService.class);

    private final Tracer tracer;

    public RetrievalAgentService(Tracer tracer) {
        this.tracer = tracer;
    }

    public RetrievedContext retrieve(String userQuery) {
        Span retrievalSpan = tracer.spanBuilder("chat retrieval-agent")
                .setSpanKind(SpanKind.CLIENT)
                // GenAI semantic convention attributes — set before the call
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.system", "openai")
                .setAttribute("gen_ai.request.model", "gpt-4o-mini")
                .setAttribute("gen_ai.request.temperature", 0.0)
                .setAttribute("agent.name", "retrieval-agent")
                .startSpan();

        try (Scope scope = retrievalSpan.makeCurrent()) {
            log.info("Retrieval agent processing query");

            // Simulated vector-store retrieval + LLM synthesis.
            // In production: call a VectorStore, then pass results to ChatClient.
            List<String> docIds = List.of("doc-policy-v3", "doc-guidelines-2024", "doc-faq-returns");
            List<String> snippets = List.of(
                    "Refund requests must be submitted within 30 days of purchase.",
                    "All customer data is handled under GDPR Article 17 compliance.",
                    "Returns accepted only for unopened merchandise in original packaging."
            );

            // Simulated token usage — record after the call so the cost is visible on the span
            int promptTokens = 450 + (userQuery.length() / 4);
            retrievalSpan.setAttribute("gen_ai.usage.input_tokens", promptTokens);
            retrievalSpan.setAttribute("gen_ai.usage.output_tokens", 280);
            retrievalSpan.setAttribute("agent.retrieval.doc_count", docIds.size());
            retrievalSpan.setAttribute("agent.retrieval.doc_ids", String.join(",", docIds));

            log.info("Retrieval agent found {} documents", docIds.size());
            return new RetrievedContext(docIds, snippets, promptTokens);

        } catch (Exception e) {
            retrievalSpan.recordException(e);
            retrievalSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            retrievalSpan.end();
        }
    }
}
