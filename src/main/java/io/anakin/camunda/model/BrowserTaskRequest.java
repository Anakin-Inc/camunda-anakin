package io.anakin.camunda.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * Input for {@link io.anakin.camunda.BrowserTaskConnectorFunction}. Mirrors {@code
 * browserTaskTool} in {@code anakin-mcp/src/tools/browser-task.ts}: {@code POST /ai/evaluate
 * {prompt, async: true, url?, session_id?, max_steps?, timeout_ms?, output_schema?}}, polled at
 * {@code GET /ai/jobs/:workflow_id} to a terminal state. Hands a natural-language instruction to
 * Anakin's browser AI agent, which drives a real stealth browser and returns the outcome.
 *
 * <p>Deliberately no credential-injection field, matching the real MCP tool's own omission (see
 * {@code browser-task.ts}'s header comment): a secret passed through this field would enter the
 * BPMN process variables / execution history in plaintext. Use {@code sessionId} (a saved,
 * encrypted browser session from Session List) for authenticated tasks instead.
 */
public record BrowserTaskRequest(
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
            description = "The task in natural language. Be specific about the goal and what to"
                + " return. Never include passwords or secrets -- use Session ID for"
                + " authenticated sites.",
            type = PropertyType.Text,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotEmpty
        String prompt,
    @TemplateProperty(
            group = "input",
            label = "URL",
            description = "Navigate here before starting. Leave empty to let the agent follow"
                + " URLs named in the prompt.",
            optional = true)
        String url,
    @TemplateProperty(
            group = "input",
            label = "Session ID",
            description = "Saved browser-session ID (from Session List) so the task runs logged"
                + " in.",
            optional = true)
        String sessionId,
    @TemplateProperty(
            group = "input",
            label = "Max Steps",
            description = "Cap on agent steps (navigation/click/type actions).",
            optional = true)
        Integer maxSteps,
    @TemplateProperty(
            group = "input",
            label = "Timeout (ms)",
            description = "Task timeout in milliseconds (server caps runs at ~330s regardless).",
            optional = true)
        Integer timeoutMs,
    @TemplateProperty(
            group = "input",
            label = "Output Schema (JSON Schema)",
            description = "JSON Schema for the result -- the agent returns structured data"
                + " conforming to it. FEEL context expression.",
            optional = true,
            feel = FeelMode.optional)
        Map<String, Object> outputSchema) {}
