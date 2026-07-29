package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;

/**
 * Input for {@link io.anakin.camunda.WireBuildConnectorFunction}. Mirrors {@code wireBuildTool} in
 * {@code anakin-mcp/src/tools/wire.ts}: {@code POST /wire/build-request {website_url, goal,
 * catalog_id?, visibility?, force?}}. Requests a brand-new Wire action for a website not yet in
 * the catalog; asynchronous, returns status "pending", charges credits (refunded automatically on
 * build failure).
 */
public record WireBuildRequest(
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
            label = "Website URL",
            description = "The site to build an action for. The domain is extracted"
                + " automatically.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String websiteUrl,
    @TemplateProperty(
            group = "input",
            label = "Goal",
            description = "Natural-language description of what the action should do or extract."
                + " Be specific -- the builder synthesizes the scraper from this.",
            type = PropertyType.Text,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String goal,
    @TemplateProperty(
            group = "input",
            label = "Catalog ID",
            description = "Optional -- attach to an existing catalog instead of creating one.",
            optional = true)
        String catalogId,
    @TemplateProperty(
            group = "input",
            label = "Visibility",
            description = "Action visibility: \"private\" or \"public\". Defaults to private.",
            optional = true,
            defaultValue = "private")
        String visibility,
    @TemplateProperty(
            group = "input",
            label = "Force",
            description = "Build even if similar actions already exist for the domain (otherwise"
                + " the request is rejected with ACTION_EXISTS).",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean force) {}
