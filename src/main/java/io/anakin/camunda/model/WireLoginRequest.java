package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * Input for {@link io.anakin.camunda.WireLoginConnectorFunction}. Mirrors {@code wireLoginTool} in
 * {@code anakin-mcp/src/tools/wire.ts}: {@code POST /wire/login {catalog_slug, identity_name?,
 * params?, source_id?, source_ref?}}. Signs in to a credentials-mode Wire site and returns a
 * credential_id usable immediately with Wire Read/Write Action.
 */
public record WireLoginRequest(
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
            description = "The catalog to sign in to (e.g. \"neb\").",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String catalogSlug,
    @TemplateProperty(
            group = "input",
            label = "Login Params",
            description = "Login fields defined by the catalog (e.g. {email, password}). Use Wire"
                + " Catalog's login_input_schema to learn the field names. FEEL context"
                + " expression, e.g. <code>={email: \"a@b.com\", password: \"...\"}</code>."
                + " Use a secret reference for the password, never a raw credential.",
            optional = true,
            feel = FeelMode.optional)
        Map<String, Object> params,
    @TemplateProperty(
            group = "input",
            label = "Identity Name",
            description = "Optional name for the identity. Derived from params in password mode;"
                + " required when using a 1Password locator.",
            optional = true)
        String identityName,
    @TemplateProperty(
            group = "onepassword",
            label = "1Password Source ID",
            description = "Optional 1Password identity-source ID (alternative to Login Params).",
            optional = true)
        String sourceId,
    @TemplateProperty(
            group = "onepassword",
            label = "1Password Item Locator",
            description = "Optional 1Password item locator {vault_id, item_id, fields} (use with"
                + " Source ID instead of Login Params). FEEL context expression.",
            optional = true,
            feel = FeelMode.optional)
        Map<String, Object> sourceRef) {}
