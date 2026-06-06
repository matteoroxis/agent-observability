package it.matteoroxis.agentobservability.domain;

import java.util.List;

/**
 * Decision produced by the Policy Agent, including token usage
 * needed to record GenAI semantic convention attributes on the span.
 */
public record PolicyResult(
        String decision,
        String policyVersion,
        List<String> sourceDocIds,
        int promptTokens,
        int completionTokens) {
}
