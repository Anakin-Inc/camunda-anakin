package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.BrowserTaskRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a natural-language task in a real cloud browser driven by an AI agent, via Anakin's {@code
 * POST /v1/ai/evaluate {prompt, async: true, url?, session_id?, max_steps?, timeout_ms?,
 * output_schema?}} (submit) then {@code GET /v1/ai/jobs/:workflowId} (poll) pair. Request/response
 * shapes mirror {@code browserTaskTool} / {@code AnakinClient.browserTask} in {@code
 * anakin-mcp/src/tools/browser-task.ts} / {@code anakin-mcp/src/client.ts} exactly, including
 * always submitting with {@code async: true} (the synchronous variant would block the HTTP request
 * for up to ~5.5 minutes, far past this project's 30s {@code REQUEST_TIMEOUT}).
 *
 * <p>Blocks the job worker thread inside {@link #execute} until the job reaches a terminal state
 * or {@link AnakinApiClient#BROWSER_TASK_POLL_TIMEOUT} (6 minutes, wider than the default 5
 * because the real API hard-caps a run at ~330s server-side) elapses -- see {@code SUBMIT.md} for
 * the sync-vs-poll design rationale (same choice as {@link UrlScraperConnectorFunction}). Unlike
 * the other polling connectors, {@code GET /ai/jobs/:workflowId} can also report {@code
 * "timed_out"} as a distinct terminal status from {@code "failed"} -- both are translated into a
 * thrown {@code ConnectorException} here, with different error codes so a BPMN error boundary
 * event can distinguish them.
 *
 * <p>Deliberately no credential-injection input -- see {@link
 * io.anakin.camunda.model.BrowserTaskRequest}'s Javadoc. Use {@code sessionId} for authenticated
 * tasks instead.
 */
@OutboundConnector(
    name = "Anakin - Browser Task",
    inputVariables = {"apiKey", "prompt", "url", "sessionId", "maxSteps", "timeoutMs", "outputSchema"},
    type = "io.anakin:browser-task:1")
@ElementTemplate(
    id = "io.anakin.connector.BrowserTask.v1",
    name = "Anakin - Browser Task",
    version = 1,
    description = "Run a natural-language task in a real AI-driven cloud browser with Anakin"
        + " (https://anakin.io). Transparently polls to completion.",
    inputDataClass = BrowserTaskRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Task input")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class BrowserTaskConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(BrowserTaskConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public BrowserTaskConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  BrowserTaskConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    BrowserTaskRequest request = context.bindVariables(BrowserTaskRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("prompt", request.prompt());
    requestBody.put("async", Boolean.TRUE);
    if (request.url() != null && !request.url().isBlank()) {
      requestBody.put("url", request.url());
    }
    if (request.sessionId() != null && !request.sessionId().isBlank()) {
      requestBody.put("session_id", request.sessionId());
    }
    if (request.maxSteps() != null) {
      requestBody.put("max_steps", request.maxSteps());
    }
    if (request.timeoutMs() != null) {
      requestBody.put("timeout_ms", request.timeoutMs());
    }
    if (request.outputSchema() != null && !request.outputSchema().isEmpty()) {
      requestBody.put("output_schema", request.outputSchema());
    }

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      Map<String, Object> accepted = client.post("/ai/evaluate", requestBody);
      Object workflowId = accepted.get("workflow_id");
      if (!(workflowId instanceof String) || ((String) workflowId).isEmpty()) {
        // Service answered synchronously (shouldn't happen with async: true, but handled rather
        // than assumed away -- matches AnakinClient.browserTask's own fallback in client.ts).
        return accepted;
      }

      Map<String, Object> job = client.pollUntilTerminal(
          "/ai/jobs/" + AnakinApiClient.encodePathSegment((String) workflowId),
          AnakinApiClient.BROWSER_TASK_POLL_TIMEOUT);

      Object status = job.get("status");
      if ("failed".equals(status) || "timed_out".equals(status)) {
        Object error = job.get("error");
        throw new ConnectorException(
            "failed".equals(status) ? "ANAKIN_JOB_FAILED" : "ANAKIN_JOB_TIMED_OUT",
            "Browser task " + status + ": " + (error != null ? error : "unknown"));
      }

      Object result = job.get("result");
      return result != null ? result : job;
    } catch (AnakinApiException e) {
      LOG.error("Anakin ai/evaluate request failed statusCode={}: {}",
          e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
