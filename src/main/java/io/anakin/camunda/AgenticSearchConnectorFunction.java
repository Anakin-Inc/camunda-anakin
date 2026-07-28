package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.AgenticSearchRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-stage AI research pipeline via Anakin's {@code POST /v1/agentic-search} (submit) then
 * {@code GET /v1/agentic-search/:jobId} (poll) pair. Returns an AI-generated summary plus optional
 * structured data matching a caller-supplied JSON Schema. Costs 10 credits (see anakin-py's {@code
 * Anakin.agentic_search} docstring).
 *
 * <p>Blocks the job worker thread inside {@link #execute} until the Anakin job reaches a terminal
 * state or {@link AnakinApiClient#POLL_TIMEOUT} elapses -- see {@code SUBMIT.md} for the
 * sync-vs-poll design rationale (same choice as {@link UrlScraperConnectorFunction}).
 */
@OutboundConnector(
    name = "Anakin - Agentic Search",
    inputVariables = {"apiKey", "prompt", "useBrowser", "schema"},
    type = "io.anakin:agentic-search:1")
@ElementTemplate(
    id = "io.anakin.connector.AgenticSearch.v1",
    name = "Anakin - Agentic Search",
    version = 1,
    description = "Multi-stage AI research pipeline with Anakin (https://anakin.io). Optional"
        + " JSON Schema for structured output.",
    inputDataClass = AgenticSearchRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Research input")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class AgenticSearchConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(AgenticSearchConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public AgenticSearchConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  AgenticSearchConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    AgenticSearchRequest request = context.bindVariables(AgenticSearchRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("prompt", request.prompt());
    requestBody.put("useBrowser", request.useBrowser() != null ? request.useBrowser() : Boolean.TRUE);
    if (request.schema() != null && !request.schema().isEmpty()) {
      requestBody.put("schema", request.schema());
    }

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      Map<String, Object> submitted = client.post("/agentic-search", requestBody);
      String jobId = AnakinApiClient.requireJobId(submitted);
      Map<String, Object> result = client.pollUntilTerminal("/agentic-search/" + jobId);

      if ("failed".equals(result.get("status"))) {
        Object error = result.get("error");
        throw new ConnectorException(
            "ANAKIN_JOB_FAILED",
            error != null ? error.toString() : "Anakin agentic-search job failed with no error detail.");
      }
      return result;
    } catch (AnakinApiException e) {
      LOG.error("Anakin agentic-search request failed for prompt={} statusCode={}: {}",
          request.prompt(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
