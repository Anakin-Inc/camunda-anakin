package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.MonitorChangesConnectorFunction}. Mirrors {@code
 * monitorChangesTool} in {@code anakin-mcp/src/tools/monitor.ts}: {@code GET
 * /monitors/:id/changes}, the detected changes for a monitor.
 */
public record MonitorChangesRequest(
    @TemplateProperty(
            group = "authentication",
            label = "Anakin API Key",
            description = "Paste a secret reference, e.g. <code>{{secrets.ANAKIN_API_KEY}}</code>,"
                + " rather than a raw key. Get a free key (300 credits, no card required) at"
                + " anakin.io/dashboard.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String apiKey,
    @TemplateProperty(
            group = "input",
            label = "Monitor ID",
            description = "The monitor ID (from Monitor List or Monitor Create).",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String monitorId) {}
