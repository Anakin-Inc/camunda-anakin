package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * Input for {@link io.anakin.camunda.WireWriteActionConnectorFunction}. Mirrors {@code
 * wireWriteActionTool} in {@code anakin-mcp/src/tools/wire.ts}: {@code POST /wire/task {action_id,
 * params?, credential_id?, identity_id?}}, for Wire actions whose {@code type} is {@code "write"}
 * (state-changing interactions on the target site: submit a form, add to a cart, etc.) --
 * discovered first via {@code wire_discover} / {@code wire_catalog}. Returns inline data directly
 * for synchronous actions, or polls {@code GET /wire/jobs/:jobId} to a terminal state for
 * asynchronous ones. Request shape is identical to {@link WireReadActionRequest} -- kept as a
 * separate class/connector so the write path carries its own, honestly distinct element template
 * (a modeler choosing this element is choosing a state-changing action, not a read).
 */
public record WireWriteActionRequest(
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
            label = "Action ID",
            description = "The action to run (from Wire Discover / Wire Catalog). Must be a"
                + " write-type action.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String actionId,
    @TemplateProperty(
            group = "input",
            label = "Params",
            description = "The action's input parameters. Shape depends on the action -- use its"
                + " parameter schema from discovery. FEEL context expression, e.g."
                + " <code>={query: \"laptop\"}</code>. Omit for actions that take none.",
            optional = true,
            feel = FeelMode.optional)
        Map<String, Object> params,
    @TemplateProperty(
            group = "auth",
            label = "Credential ID",
            description = "Required when the action's auth_mode is \"required\"; honored when"
                + " \"optional\". Get one from Wire Identities or Wire Login.",
            optional = true)
        String credentialId,
    @TemplateProperty(
            group = "auth",
            label = "Identity ID",
            description = "Optional identity selector -- the server resolves a credential from it"
                + " (alternative to Credential ID).",
            optional = true)
        String identityId) {}
