package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.WireIdentitiesConnectorFunction}. Mirrors {@code
 * wireIdentitiesTool} in {@code anakin-mcp/src/tools/wire.ts}: {@code GET
 * /wire/identities?catalog_id=}, lists saved Wire identities and their credentials.
 */
public record WireIdentitiesRequest(
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
            label = "Catalog ID",
            description = "Optional -- restrict to identities for a single catalog.",
            optional = true)
        String catalogId) {}
