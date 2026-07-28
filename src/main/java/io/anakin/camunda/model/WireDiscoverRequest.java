package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.WireDiscoverConnectorFunction}. Mirrors {@code
 * wireDiscoverTool} in {@code anakin-mcp/src/tools/wire.ts}: {@code GET
 * /wire/resolve?q=&limit=}, natural-language intent to ranked candidate Wire actions.
 */
public record WireDiscoverRequest(
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
            label = "Intent",
            description = "The intent in natural language, e.g. \"top phones on walmart\","
                + " \"search airbnb listings in Lisbon\".",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String q,
    @TemplateProperty(
            group = "input",
            label = "Limit",
            description = "Maximum number of candidate actions to return. Defaults to 5.",
            optional = true,
            defaultValue = "5",
            defaultValueType = DefaultValueType.Number)
        Integer limit) {}
