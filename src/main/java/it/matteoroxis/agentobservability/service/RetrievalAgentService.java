package it.matteoroxis.agentobservability.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import it.matteoroxis.agentobservability.domain.RetrievedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Retrieval Agent: calls Claude Haiku 4.5 to synthesize relevant context from the
 * knowledge base based on the user query.
 *
 * Token counts and document IDs are recorded as span attributes following the
 * GenAI semantic conventions so Jaeger shows real cost per trace.
 */
@Service
public class RetrievalAgentService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalAgentService.class);

    private static final List<String> DOC_IDS =
            List.of("doc-policy-v3", "doc-guidelines-2024", "doc-faq-returns");

    private static final String SYSTEM_PROMPT = """
            You are a retrieval agent for a customer service knowledge base.
            The knowledge base contains the following documents:
            - doc-policy-v3: "Refund requests must be submitted within 30 days of purchase."
            - doc-guidelines-2024: "All customer data is handled under GDPR Article 17 compliance."
            - doc-faq-returns: "Returns accepted only for unopened merchandise in original packaging."
            Given the user query, synthesize the most relevant excerpts from these documents
            into a concise paragraph. Do not add information not present in the knowledge base.
            """;

    private final Tracer tracer;
    private final ChatClient chatClient;

    public RetrievalAgentService(Tracer tracer, ChatClient.Builder chatClientBuilder) {
        this.tracer = tracer;
        this.chatClient = chatClientBuilder.build();
    }

    public RetrievedContext retrieve(String userQuery) {
        Span retrievalSpan = tracer.spanBuilder("chat retrieval-agent")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.system", "anthropic")
                .setAttribute("gen_ai.request.model", "claude-haiku-4-5")
                .setAttribute("gen_ai.request.temperature", 0.0)
                .setAttribute("agent.name", "retrieval-agent")
                .startSpan();

        try (Scope scope = retrievalSpan.makeCurrent()) {
            log.info("Retrieval agent processing query");

            ChatResponse chatResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userQuery)
                    .options(AnthropicChatOptions.builder()
                            .model("claude-haiku-4-5")
                            .temperature(0.0)
                            .build())
                    .call()
                    .chatResponse();

            String synthesized = chatResponse.getResult().getOutput().getText();
            var usage = chatResponse.getMetadata().getUsage();
            int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            int completionTokens = usage.getGenerationTokens() != null ? usage.getGenerationTokens() : 0;

            retrievalSpan.setAttribute("gen_ai.usage.input_tokens", promptTokens);
            retrievalSpan.setAttribute("gen_ai.usage.output_tokens", completionTokens);
            retrievalSpan.setAttribute("agent.retrieval.doc_count", DOC_IDS.size());
            retrievalSpan.setAttribute("agent.retrieval.doc_ids", String.join(",", DOC_IDS));

            log.info("Retrieval agent synthesized context ({} input / {} output tokens)",
                    promptTokens, completionTokens);
            return new RetrievedContext(DOC_IDS, List.of(synthesized), promptTokens);

        } catch (Exception e) {
            retrievalSpan.recordException(e);
            retrievalSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            retrievalSpan.end();
        }
    }
}
