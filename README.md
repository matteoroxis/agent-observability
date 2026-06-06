# agent-observability

Demo project for the foojay.io article  
**"When Your Agents Go Dark: Observability in Multi-Agent Systems with OpenTelemetry"**

Simulates a four-agent orchestration pipeline fully instrumented with the OpenTelemetry
Java API and the GenAI semantic conventions. Every agent invocation produces a child span
that is nested under the orchestrator's root span, giving you one end-to-end trace per
HTTP request in Jaeger.

```
agent.orchestrator
  └── chat retrieval-agent   (gen_ai.usage.input_tokens, agent.retrieval.doc_ids, …)
  └── chat policy-agent      (gen_ai.usage.input_tokens, agent.policy.version, …)
  └── chat summarizer-agent  (gen_ai.usage.input_tokens, agent.handoff.reason, …)
```

---

## Tech stack

| Layer        | Technology                                        |
|--------------|---------------------------------------------------|
| Language     | Java 21                                           |
| Framework    | Spring Boot 3.4.1                                 |
| Tracing API  | OpenTelemetry Java (via `micrometer-tracing-bridge-otel`) |
| Export       | OTLP HTTP → Jaeger all-in-one                    |
| Build        | Maven                                             |

---

## Quick start

### 1. Start Jaeger

```bash
docker compose up -d
```

Jaeger UI will be available at <http://localhost:16686>.

### 2. Run the application

```bash
mvn spring-boot:run
```

> If you prefer the Maven wrapper, copy `mvnw` / `mvnw.cmd` from any sibling project.

### 3. Send a request

```bash
curl -s -X POST http://localhost:8080/api/orchestrate \
     -H "Content-Type: application/json" \
     -d '{"query": "Can I return this product?"}' | jq
```

### 4. View the trace

Open Jaeger UI → **Search** → Service: `agent-observability` → **Find Traces**.

You will see a single trace with four nested spans, each carrying the GenAI
semantic convention attributes described in the article
(`gen_ai.usage.input_tokens`, `gen_ai.request.model`, `agent.policy.version`, …).

---

## Project structure

```
src/main/java/it/matteoroxis/agentobservability/
├── AgentObservabilityApplication.java
├── config/
│   └── TracingConfig.java          ← exposes OTel Tracer bean
├── controller/
│   └── OrchestrationController.java
├── domain/
│   ├── AgentContext.java
│   ├── PolicyResult.java
│   └── RetrievedContext.java
├── dto/
│   ├── request/UserQueryRequest.java
│   └── response/OrchestrationResponse.java
├── exception/
│   ├── AgentException.java
│   └── GlobalExceptionHandler.java
└── service/
    ├── OrchestratorService.java     ← root span, coordinates all agents
    ├── RetrievalAgentService.java   ← child span: retrieval
    ├── PolicyAgentService.java      ← child span: policy evaluation
    └── SummarizerAgentService.java  ← child span: reply composition
```

---

## Key instrumentation pattern

Each agent service follows the same pattern from the article:

```java
Span agentSpan = tracer.spanBuilder("chat policy-agent")
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute("gen_ai.operation.name", "chat")
        .setAttribute("gen_ai.system", "openai")
        .setAttribute("gen_ai.request.model", "gpt-4o")
        .setAttribute("gen_ai.request.temperature", 0.2)
        .startSpan();

try (Scope scope = agentSpan.makeCurrent()) {
    // ... call the model / tool ...
    agentSpan.setAttribute("gen_ai.usage.input_tokens", result.promptTokens());
    agentSpan.setAttribute("gen_ai.usage.output_tokens", result.completionTokens());
    agentSpan.setAttribute("agent.policy.version", result.policyVersion());
} catch (Exception e) {
    agentSpan.recordException(e);
    agentSpan.setStatus(StatusCode.ERROR, e.getMessage());
    throw e;
} finally {
    agentSpan.end();
}
```

---

## References

- [OpenTelemetry — Semantic Conventions for Generative AI](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- [Spring Boot — Tracing](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.micrometer-tracing)
- [Jaeger — Getting started](https://www.jaegertracing.io/docs/latest/getting-started/)

