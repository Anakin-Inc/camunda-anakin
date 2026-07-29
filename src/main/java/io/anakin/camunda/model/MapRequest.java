package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.MapConnectorFunction}. Field set and defaults mirror {@code
 * AnakinClient.map} in {@code anakin-mcp/src/client.ts} exactly: {@code {url, limit=100, depth=2,
 * limitPerLevel=100, includeSubdomains=false, includeExternalLinks=false, useBrowser=false,
 * search?}}.
 */
public record MapRequest(
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
            label = "URL",
            description = "The starting URL for discovery (typically a homepage or section root).",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String url,
    @TemplateProperty(
            group = "input",
            label = "Limit",
            description = "Maximum number of URLs to return overall. Defaults to 100.",
            optional = true,
            defaultValue = "100",
            defaultValueType = DefaultValueType.Number)
        Integer limit,
    @TemplateProperty(
            group = "input",
            label = "Depth",
            description = "How many link-hops from the starting URL to follow. Defaults to 2.",
            optional = true,
            defaultValue = "2",
            defaultValueType = DefaultValueType.Number)
        Integer depth,
    @TemplateProperty(
            group = "input",
            label = "Limit Per Level",
            description = "Maximum URLs collected per depth level (controls breadth). Defaults to"
                + " 100.",
            optional = true,
            defaultValue = "100",
            defaultValueType = DefaultValueType.Number)
        Integer limitPerLevel,
    @TemplateProperty(
            group = "input",
            label = "Include Subdomains",
            description = "Include URLs on subdomains of the starting host.",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean includeSubdomains,
    @TemplateProperty(
            group = "input",
            label = "Include External Links",
            description = "Also collect (but do not follow) external links.",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean includeExternalLinks,
    @TemplateProperty(
            group = "input",
            label = "Use Browser",
            description = "Render with a headless browser first -- for JS-heavy sites (SPAs).",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean useBrowser,
    @TemplateProperty(
            group = "input",
            label = "Search",
            description = "Optional keyword filter -- only return URLs whose path/title matches.",
            optional = true)
        String search) {}
