package it.matteoroxis.agentobservability.domain;

import java.util.List;

/**
 * Documents and snippets retrieved by the Retrieval Agent from the vector store.
 */
public record RetrievedContext(
        List<String> docIds,
        List<String> snippets,
        int promptTokens) {
}
