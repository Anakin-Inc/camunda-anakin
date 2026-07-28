package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.SearchConnectorFunction}. Field set and defaults mirror
 * {@code Anakin.search} in {@code anakin-py/src/anakin/client.py} exactly: {@code {prompt,
 * limit=5}}. Synchronous endpoint -- no job id, no polling.
 */
public record SearchRequest(
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
            label = "Prompt",
            description = "Natural-language search query, e.g. \"top 5 CRM platforms for"
                + " startups\".",
            type = PropertyType.Text,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String prompt,
    @TemplateProperty(
            group = "input",
            label = "Limit",
            description = "Maximum number of results to return. Defaults to 5.",
            optional = true,
            defaultValue = "5",
            defaultValueType = DefaultValueType.Number)
        Integer limit) {}
