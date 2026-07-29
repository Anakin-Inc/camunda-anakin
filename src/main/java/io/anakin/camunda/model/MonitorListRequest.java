package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.MonitorListConnectorFunction}. Mirrors {@code
 * monitorListTool} in {@code anakin-mcp/src/tools/monitor.ts}: {@code GET /monitors} (list all),
 * or {@code GET /monitors/:id} when {@code id} is supplied (one monitor's full configuration and
 * status).
 */
public record MonitorListRequest(
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
            description = "Fetch just this monitor instead of the full list. Leave empty to list"
                + " all monitors.",
            optional = true)
        String monitorId) {}
