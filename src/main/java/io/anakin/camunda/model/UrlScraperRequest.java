package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Input for {@link io.anakin.camunda.UrlScraperConnectorFunction}. Field set and defaults mirror
 * {@code Anakin.scrape} in {@code anakin-py/src/anakin/client.py} exactly: {@code {url,
 * formats=["markdown"], country='us', useBrowser=false, generateJson=false, forceFresh=false,
 * sessionId?, sessionName?}}.
 */
public record UrlScraperRequest(
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
            description = "The page to scrape, including scheme (https://...).",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String url,
    @TemplateProperty(
            group = "input",
            label = "Formats",
            description = "Content formats to extract: markdown, html, cleanedHtml, json, links,"
                + " images, screenshot, screenshotFullPage, summary. FEEL list expression, e.g."
                + " <code>=[\"markdown\",\"html\"]</code>. Defaults to just markdown.",
            optional = true,
            feel = FeelMode.optional)
        List<String> formats,
    @TemplateProperty(
            group = "input",
            label = "Country",
            description = "Two-letter proxy exit country code, e.g. \"us\", \"gb\", \"de\".",
            optional = true,
            defaultValue = "us")
        String country,
    @TemplateProperty(
            group = "input",
            label = "Use Browser",
            description = "Render the page in a real browser first -- for JS-heavy or anti-bot"
                + " sites. Slower and costs more credits than a plain fetch.",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean useBrowser,
    @TemplateProperty(
            group = "input",
            label = "Generate JSON",
            description = "Also ask Anakin for an AI-structured JSON extraction of the page"
                + " content.",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean generateJson,
    @TemplateProperty(
            group = "input",
            label = "Force Fresh",
            description = "Bypass the cache and re-scrape even if a recent cached result exists.",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean forceFresh,
    @TemplateProperty(
            group = "session",
            label = "Session ID",
            description = "Reuse an existing saved browser session (cookies/login) by ID."
                + " Optional.",
            optional = true)
        String sessionId,
    @TemplateProperty(
            group = "session",
            label = "Session Name",
            description = "Reuse an existing saved browser session (cookies/login) by name."
                + " Optional.",
            optional = true)
        String sessionName) {}
