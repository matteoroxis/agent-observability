package it.matteoroxis.agentobservability.domain;

/**
 * Full context passed to downstream agents: the original user query
 * together with the documents retrieved by the Retrieval Agent.
 */
public record AgentContext(
        String userQuery,
        RetrievedContext retrievedContext) {
}
