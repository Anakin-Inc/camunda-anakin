package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.AiVisibilitySourcesConnectorFunction}. Mirrors {@code
 * aiVisibilitySourcesTool} in {@code anakin-mcp/src/tools/ai-visibility.ts}: {@code GET
 * /ai-visibility/sources}, no parameters beyond authentication.
 */
public record AiVisibilitySourcesRequest(
    @TemplateProperty(
            group = "authentication",
            label = "Anakin API Key",
            description = "Paste a secret reference, e.g. <code>{{secrets.ANAKIN_API_KEY}}</code>,"
                + " rather than a raw key. Get a free key (300 credits, no card required) at"
                + " anakin.io/dashboard.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String apiKey) {}
