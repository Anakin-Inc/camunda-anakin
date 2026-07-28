package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Input for {@link io.anakin.camunda.AiVisibilitySearchConnectorFunction}. Mirrors {@code
 * aiVisibilitySearchTool} in {@code anakin-mcp/src/tools/ai-visibility.ts}: {@code POST
 * /ai-visibility/search {query, sources?, country?}}, polled at {@code GET
 * /ai-visibility/search/:search_id} to a terminal state. Fans one query out to multiple AI answer
 * engines (ChatGPT, Gemini, Google AI Overview, ...) and compares their answers.
 */
public record AiVisibilitySearchRequest(
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
            label = "Query",
            description = "The question to ask every engine (max 2000 characters).",
            type = PropertyType.Text,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String query,
    @TemplateProperty(
            group = "input",
            label = "Sources",
            description = "Engine slugs to query (see Ai Visibility Sources). FEEL list"
                + " expression, e.g. <code>=[\"chatgpt\",\"gemini\"]</code>. Omit to query all"
                + " enabled engines.",
            optional = true,
            feel = FeelMode.optional)
        List<String> sources,
    @TemplateProperty(
            group = "input",
            label = "Country",
            description = "Two-letter ISO country for the search geography (proxy exit). Defaults"
                + " to \"us\".",
            optional = true,
            defaultValue = "us")
        String country,
    @TemplateProperty(
            group = "input",
            label = "Include Full Content",
            description = "Include each engine's raw full answer in the results (large). Defaults"
                + " to false -- summaries and the synthesis are returned regardless.",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean includeFullContent) {}
