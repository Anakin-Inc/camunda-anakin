package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.WireCatalogConnectorFunction}. Mirrors {@code
 * wireCatalogTool} in {@code anakin-mcp/src/tools/wire.ts}: {@code GET /wire/catalog} (list every
 * catalog) or {@code GET /wire/catalog/:slug} (one catalog's full action list with param schemas)
 * when {@code slug} is supplied.
 */
public record WireCatalogRequest(
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
            label = "Catalog Slug",
            description = "Catalog slug to inspect (e.g. \"walmart\"). Leave empty to list all"
                + " catalogs.",
            optional = true)
        String slug) {}
