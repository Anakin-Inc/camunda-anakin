package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.SessionListConnectorFunction}. Mirrors {@code
 * sessionListTool} in {@code anakin-mcp/src/tools/sessions.ts}: {@code GET
 * /sessions?domain=}, lists saved browser sessions (encrypted login states).
 */
public record SessionListRequest(
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
            label = "Domain",
            description = "Filter to sessions for one website domain, e.g. \"amazon.com\".",
            optional = true)
        String domain) {}
