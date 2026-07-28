package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * Input for {@link io.anakin.camunda.AgenticSearchConnectorFunction}. Field set and defaults
 * mirror {@code Anakin.agentic_search} in {@code anakin-py/src/anakin/client.py} exactly: {@code
 * {prompt, useBrowser=true, schema?}}.
 */
public record AgenticSearchRequest(
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
            description = "Research goal for the multi-stage agentic pipeline, e.g. \"compare"
                + " pricing across the top 3 project management tools\".",
            type = PropertyType.Text,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String prompt,
    @TemplateProperty(
            group = "input",
            label = "Use Browser",
            description = "Allow the agent to render pages in a real browser while researching.",
            optional = true,
            defaultValue = "true",
            defaultValueType = DefaultValueType.Boolean)
        Boolean useBrowser,
    @TemplateProperty(
            group = "input",
            label = "Result Schema (JSON Schema)",
            description = "Optional JSON Schema describing the structured fields to extract into"
                + " the response. Leave empty for a free-form summary only.",
            optional = true,
            feel = FeelMode.optional)
        Map<String, Object> schema) {}
