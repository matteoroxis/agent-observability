package it.matteoroxis.agentobservability.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import it.matteoroxis.agentobservability.domain.AgentContext;
import it.matteoroxis.agentobservability.domain.PolicyResult;
import it.matteoroxis.agentobservability.domain.RetrievedContext;
import it.matteoroxis.agentobservability.dto.response.OrchestrationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock
    private RetrievalAgentService retrievalAgent;

    @Mock
    private PolicyAgentService policyAgent;

    @Mock
    private SummarizerAgentService summarizerAgent;

    private OrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        // Use a no-op tracer so spans are created but never exported —
        // no OTel infrastructure needed in unit tests.
        Tracer tracer = OpenTelemetry.noop().getTracer("test-scope");
        orchestratorService = new OrchestratorService(tracer, retrievalAgent, policyAgent, summarizerAgent);
    }

    @Test
    @DisplayName("orchestrate - query valida - risposta con policy e documenti sorgente")
    void orchestrate_validQuery_returnsCompleteResponse() {
        // Given
        String userQuery = "Can I return this product?";
        RetrievedContext retrievedContext = new RetrievedContext(
                List.of("doc-policy-v3", "doc-faq-returns"),
                List.of("Refunds within 30 days.", "Original packaging required."),
                450);
        PolicyResult policyResult = new PolicyResult(
                "APPROVED", "policy-v3.2",
                List.of("doc-policy-v3", "doc-faq-returns"),
                820, 180);
        String expectedAnswer = "Your return request has been approved.";

        when(retrievalAgent.retrieve(userQuery)).thenReturn(retrievedContext);
        when(policyAgent.evaluate(any(AgentContext.class))).thenReturn(policyResult);
        when(summarizerAgent.summarize(any(AgentContext.class), eq(policyResult))).thenReturn(expectedAnswer);

        // When
        OrchestrationResponse response = orchestratorService.orchestrate(userQuery);

        // Then
        assertThat(response.answer()).isEqualTo(expectedAnswer);
        assertThat(response.policyVersion()).isEqualTo("policy-v3.2");
        assertThat(response.sourceDocIds()).containsExactlyInAnyOrder("doc-policy-v3", "doc-faq-returns");
    }

    @Test
    @DisplayName("orchestrate - retrieval agent fallisce - eccezione propagata")
    void orchestrate_retrievalAgentFails_throwsException() {
        // Given
        when(retrievalAgent.retrieve(any())).thenThrow(new RuntimeException("Vector store unreachable"));

        // When / Then
        assertThatThrownBy(() -> orchestratorService.orchestrate("test query"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Vector store unreachable");
    }

    @Test
    @DisplayName("orchestrate - policy agent fallisce - eccezione propagata")
    void orchestrate_policyAgentFails_throwsException() {
        // Given
        RetrievedContext retrievedContext = new RetrievedContext(
                List.of("doc-1"), List.of("snippet"), 300);

        when(retrievalAgent.retrieve(any())).thenReturn(retrievedContext);
        when(policyAgent.evaluate(any(AgentContext.class)))
                .thenThrow(new RuntimeException("Policy service timeout"));

        // When / Then
        assertThatThrownBy(() -> orchestratorService.orchestrate("test query"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Policy service timeout");
    }
}
