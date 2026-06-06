package it.matteoroxis.agentobservability.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the OTel {@link Tracer} as a Spring bean.
 *
 * Spring Boot's OpenTelemetryAutoConfiguration (triggered by
 * micrometer-tracing-bridge-otel on the classpath) creates and registers
 * an {@link OpenTelemetry} SDK bean — we just derive a named Tracer from it.
 * All spans created with this tracer are automatically exported via the
 * OTLP exporter configured in application.properties.
 */
@Configuration
public class TracingConfig {

    @Bean
    public Tracer agentTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("it.matteoroxis.agent-observability", "0.0.1-SNAPSHOT");
    }
}
