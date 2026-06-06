package it.matteoroxis.agentobservability.dto.response;

import java.util.List;

public record OrchestrationResponse(
        String answer,
        String policyVersion,
        List<String> sourceDocIds) {
}
