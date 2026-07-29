package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.SessionDeleteConnectorFunction}. Mirrors {@code
 * sessionDeleteTool} in {@code anakin-mcp/src/tools/sessions.ts}: {@code DELETE
 * /sessions/:id}. Irreversible -- destroys the saved encrypted login state.
 */
public record SessionDeleteRequest(
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
            label = "Session ID",
            description = "The session ID to delete (from Session List). Irreversible.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String sessionId) {}
