package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Input for {@link io.anakin.camunda.CrawlConnectorFunction}. Field set and defaults mirror
 * {@code AnakinClient.crawl} in {@code anakin-mcp/src/client.ts} exactly: {@code {url,
 * maxPages=10, depth=1, country='us', useBrowser=false, includePatterns?, excludePatterns?,
 * sessionId?, sessionName?}}.
 */
public record CrawlRequest(
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
            description = "Starting URL.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String url,
    @TemplateProperty(
            group = "input",
            label = "Max Pages",
            description = "Hard cap on pages fetched. Defaults to 10.",
            optional = true,
            defaultValue = "10",
            defaultValueType = DefaultValueType.Number)
        Integer maxPages,
    @TemplateProperty(
            group = "input",
            label = "Depth",
            description = "Link-hops from the starting URL to follow. Defaults to 1.",
            optional = true,
            defaultValue = "1",
            defaultValueType = DefaultValueType.Number)
        Integer depth,
    @TemplateProperty(
            group = "input",
            label = "Country",
            description = "Two-letter proxy egress country code.",
            optional = true,
            defaultValue = "us")
        String country,
    @TemplateProperty(
            group = "input",
            label = "Use Browser",
            description = "Render each page in a headless browser first -- for JS-heavy sites"
                + " (SPAs).",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean useBrowser,
    @TemplateProperty(
            group = "input",
            label = "Include Patterns",
            description = "Glob/regex patterns. Only URLs matching at least one pattern are"
                + " fetched. FEEL list expression, e.g. <code>=[\"/blog/*\"]</code>.",
            optional = true,
            feel = FeelMode.optional)
        List<String> includePatterns,
    @TemplateProperty(
            group = "input",
            label = "Exclude Patterns",
            description = "Glob/regex patterns. URLs matching any pattern are skipped. FEEL list"
                + " expression, e.g. <code>=[\"/admin/*\"]</code>.",
            optional = true,
            feel = FeelMode.optional)
        List<String> excludePatterns,
    @TemplateProperty(
            group = "session",
            label = "Session ID",
            description = "Optional saved-browser-session ID for login-protected sites.",
            optional = true)
        String sessionId,
    @TemplateProperty(
            group = "session",
            label = "Session Name",
            description = "Optional saved-browser-session name.",
            optional = true)
        String sessionName) {}
