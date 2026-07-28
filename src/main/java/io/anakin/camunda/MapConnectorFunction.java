package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.MapRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers all reachable URLs under a site via Anakin's {@code POST /v1/map} (submit) then
 * {@code GET /v1/map/:jobId} (poll) pair. Request/response shapes mirror {@code AnakinClient.map}
 * in {@code anakin-mcp/src/client.ts} exactly -- read directly from that source, not inferred.
 *
 * <p>Blocks the job worker thread inside {@link #execute} until the Anakin job reaches a terminal
 * state or {@link AnakinApiClient#POLL_TIMEOUT} elapses -- see {@code SUBMIT.md} for the
 * sync-vs-poll design rationale (same choice as {@link UrlScraperConnectorFunction}).
 */
@OutboundConnector(
    name = "Anakin - Map Site",
    inputVariables = {
      "apiKey", "url", "limit", "depth", "limitPerLevel", "includeSubdomains",
      "includeExternalLinks", "useBrowser", "search"
    },
    type = "io.anakin:map:1")
@ElementTemplate(
    id = "io.anakin.connector.Map.v1",
    name = "Anakin - Map Site",
    version = 1,
    description = "Discover all reachable URLs under a site with Anakin (https://anakin.io) --"
        + " useful for understanding a domain's structure before crawling.",
    inputDataClass = MapRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Map input")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class MapConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(MapConnectorFunction.class);

  private static final int DEFAULT_LIMIT = 100;
  private static final int DEFAULT_DEPTH = 2;
  private static final int DEFAULT_LIMIT_PER_LEVEL = 100;

  private final Function<String, AnakinApiClient> clientFactory;

  public MapConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  MapConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    MapRequest request = context.bindVariables(MapRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("url", request.url());
    requestBody.put("limit", request.limit() != null ? request.limit() : DEFAULT_LIMIT);
    requestBody.put("depth", request.depth() != null ? request.depth() : DEFAULT_DEPTH);
    requestBody.put("limitPerLevel",
        request.limitPerLevel() != null ? request.limitPerLevel() : DEFAULT_LIMIT_PER_LEVEL);
    requestBody.put("includeSubdomains",
        request.includeSubdomains() != null ? request.includeSubdomains() : Boolean.FALSE);
    requestBody.put("includeExternalLinks",
        request.includeExternalLinks() != null ? request.includeExternalLinks() : Boolean.FALSE);
    requestBody.put("useBrowser", request.useBrowser() != null ? request.useBrowser() : Boolean.FALSE);
    if (request.search() != null && !request.search().isBlank()) {
      requestBody.put("search", request.search());
    }

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      Map<String, Object> submitted = client.post("/map", requestBody);
      String jobId = AnakinApiClient.requireJobId(submitted);
      Map<String, Object> result = client.pollUntilTerminal("/map/" + jobId);

      if ("failed".equals(result.get("status"))) {
        Object error = result.get("error");
        throw new ConnectorException(
            "ANAKIN_JOB_FAILED",
            error != null ? error.toString() : "Anakin map job failed with no error detail.");
      }
      return result;
    } catch (AnakinApiException e) {
      LOG.error("Anakin map request failed for url={} statusCode={}: {}",
          request.url(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
