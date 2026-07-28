package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.MonitorControlConnectorFunction}. Mirrors {@code
 * monitorControlTool} in {@code anakin-mcp/src/tools/monitor.ts}: {@code POST
 * /monitors/:id/pause}, {@code POST /monitors/:id/resume}, {@code POST /monitors/:id/run}, or
 * {@code DELETE /monitors/:id} depending on {@code action}.
 */
public record MonitorControlRequest(
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
        String monitorId,
    @TemplateProperty(
            group = "input",
            label = "Action",
            description = "What to do with the monitor: \"pause\" stops scheduled checks,"
                + " \"resume\" restarts them, \"run_now\" triggers an immediate billed check, or"
                + " \"delete\" permanently removes the monitor and its history.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String action) {}
