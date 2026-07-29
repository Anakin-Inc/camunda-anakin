package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Input for {@link io.anakin.camunda.MonitorCreateConnectorFunction}. Mirrors {@code
 * monitorCreateTool} in {@code anakin-mcp/src/tools/monitor.ts}: {@code POST /monitors {url,
 * intervalMinutes, scope?, watchMode?, watchFormat?, outputSchema?, aiMode?, aiGoal?, useBrowser?,
 * country?, sessionId?, isActive?, expiresAt?, alertWebhookUrl?, alertEmails?, maxPages?,
 * maxDepth?, includePatterns?, excludePatterns?, wireActionId?, wireCatalogSlug?,
 * wireCredentialId?, wireParams?, wireWatchPaths?}}. Unlike the other connectors in this project,
 * only the fields a caller actually sets are sent -- the API applies its own defaults for
 * everything else (matching {@code AnakinClient.monitorCreate}'s "only send fields the caller set"
 * comment in {@code client.ts}), so most fields here are left absent from the request body rather
 * than defaulted client-side; see {@code MonitorCreateConnectorFunction}.
 */
public record MonitorCreateRequest(
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
            group = "target",
            label = "URL",
            description = "The URL to watch (root URL for site scope; the Wire site's URL for"
                + " wire scope).",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String url,
    @TemplateProperty(
            group = "target",
            label = "Interval (minutes)",
            description = "Check frequency in minutes. Minimum 15.")
        @NotNull
        Integer intervalMinutes,
    @TemplateProperty(
            group = "target",
            label = "Scope",
            description = "What to monitor: \"page\" (default), \"site\", or \"wire\".",
            optional = true,
            defaultValue = "page")
        String scope,
    @TemplateProperty(
            group = "watch",
            label = "Watch Mode",
            description = "\"full_page\" (2 credits/check, compares the whole page) or"
                + " \"specific_data\" (3 credits/check, extracts only the Output Schema fields"
                + " with AI). Defaults to full_page.",
            optional = true,
            defaultValue = "full_page")
        String watchMode,
    @TemplateProperty(
            group = "watch",
            label = "Watch Format",
            description = "Format compared in full_page mode: markdown, html, or cleaned_html."
                + " Defaults to markdown.",
            optional = true,
            defaultValue = "markdown")
        String watchFormat,
    @TemplateProperty(
            group = "watch",
            label = "Output Schema (JSON Schema)",
            description = "JSON Schema of the fields to track. Required when Watch Mode is"
                + " specific_data. FEEL context expression.",
            optional = true,
            feel = FeelMode.optional)
        Map<String, Object> outputSchema,
    @TemplateProperty(
            group = "watch",
            label = "AI Mode",
            description = "AI meaningful-change filtering: ignores trivial noise (ads,"
                + " timestamps) and summarizes real changes. +1 credit per check.",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean aiMode,
    @TemplateProperty(
            group = "watch",
            label = "AI Goal",
            description = "Natural-language description of which changes count as meaningful"
                + " (used with AI Mode), e.g. \"only when the price drops or it goes out of"
                + " stock\".",
            optional = true)
        String aiGoal,
    @TemplateProperty(
            group = "fetch",
            label = "Use Browser",
            description = "Render checks with a stealth headless browser (needed for JS-heavy"
                + " pages). Forced true server-side when Session ID is set.",
            optional = true,
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean useBrowser,
    @TemplateProperty(
            group = "fetch",
            label = "Country",
            description = "Two-letter proxy country code. Defaults to \"us\".",
            optional = true,
            defaultValue = "us")
        String country,
    @TemplateProperty(
            group = "fetch",
            label = "Session ID",
            description = "Saved browser-session ID for monitoring login-protected pages (see"
                + " Session List).",
            optional = true)
        String sessionId,
    @TemplateProperty(
            group = "schedule",
            label = "Active",
            description = "Start running immediately. Defaults to true.",
            optional = true,
            defaultValue = "true",
            defaultValueType = DefaultValueType.Boolean)
        Boolean isActive,
    @TemplateProperty(
            group = "schedule",
            label = "Expires At",
            description = "Optional end date (ISO 8601 timestamp or YYYY-MM-DD); the monitor"
                + " auto-pauses when it passes.",
            optional = true)
        String expiresAt,
    @TemplateProperty(
            group = "alerts",
            label = "Alert Webhook URL",
            description = "Webhook URL that receives signed change alerts.",
            optional = true)
        String alertWebhookUrl,
    @TemplateProperty(
            group = "alerts",
            label = "Alert Emails",
            description = "Comma-separated email recipients for change alerts.",
            optional = true)
        String alertEmails,
    @TemplateProperty(
            group = "site",
            label = "Max Pages",
            description = "Site scope: max pages crawled per run.",
            optional = true)
        Integer maxPages,
    @TemplateProperty(
            group = "site",
            label = "Max Depth",
            description = "Site scope: crawl depth (1-5). Defaults to 2.",
            optional = true)
        Integer maxDepth,
    @TemplateProperty(
            group = "site",
            label = "Include Patterns",
            description = "Site scope: glob patterns or hand-picked same-site URLs to track. FEEL"
                + " list expression.",
            optional = true,
            feel = FeelMode.optional)
        List<String> includePatterns,
    @TemplateProperty(
            group = "site",
            label = "Exclude Patterns",
            description = "Site scope: glob patterns to skip. FEEL list expression.",
            optional = true,
            feel = FeelMode.optional)
        List<String> excludePatterns,
    @TemplateProperty(
            group = "wire",
            label = "Wire Action ID",
            description = "Wire scope (required there): the Wire action run each check, e.g."
                + " \"amazon.search_products\" (see Wire Discover).",
            optional = true)
        String wireActionId,
    @TemplateProperty(
            group = "wire",
            label = "Wire Catalog Slug",
            description = "Wire scope: catalog slug of the Wire site.",
            optional = true)
        String wireCatalogSlug,
    @TemplateProperty(
            group = "wire",
            label = "Wire Credential ID",
            description = "Wire scope: credential ID when the action needs auth (see Wire"
                + " Identities).",
            optional = true)
        String wireCredentialId,
    @TemplateProperty(
            group = "wire",
            label = "Wire Params",
            description = "Wire scope: parameters passed to the action each check. FEEL context"
                + " expression.",
            optional = true,
            feel = FeelMode.optional)
        Map<String, Object> wireParams,
    @TemplateProperty(
            group = "wire",
            label = "Wire Watch Paths",
            description = "Wire scope: JSON paths to diff instead of the whole response. FEEL"
                + " list expression.",
            optional = true,
            feel = FeelMode.optional)
        List<String> wireWatchPaths) {}
