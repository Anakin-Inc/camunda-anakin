package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.CrawlRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bulk-fetches markdown across a site via Anakin's {@code POST /v1/crawl} (submit) then {@code
 * GET /v1/crawl/:jobId} (poll) pair. Request/response shapes mirror {@code AnakinClient.crawl} in
 * {@code anakin-mcp/src/client.ts} exactly -- read directly from that source, not inferred.
 *
 * <p>Blocks the job worker thread inside {@link #execute} until the Anakin job reaches a terminal
 * state or {@link AnakinApiClient#POLL_TIMEOUT} elapses -- see {@code SUBMIT.md} for the
 * sync-vs-poll design rationale (same choice as {@link UrlScraperConnectorFunction}).
 */
@OutboundConnector(
    name = "Anakin - Crawl Site",
    inputVariables = {
      "apiKey", "url", "maxPages", "depth", "country", "useBrowser", "includePatterns",
      "excludePatterns", "sessionId", "sessionName"
    },
    type = "io.anakin:crawl:1")
@ElementTemplate(
    id = "io.anakin.connector.Crawl.v1",
    name = "Anakin - Crawl Site",
    version = 1,
    description = "Bulk-fetch markdown across a site with Anakin (https://anakin.io) -- catalog"
        + " ingestion, site-wide RAG corpus building.",
    inputDataClass = CrawlRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Crawl input"),
      @PropertyGroup(id = "session", label = "Browser session (optional)")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class CrawlConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(CrawlConnectorFunction.class);

  private static final int DEFAULT_MAX_PAGES = 10;
  private static final int DEFAULT_DEPTH = 1;

  private final Function<String, AnakinApiClient> clientFactory;

  public CrawlConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  CrawlConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    CrawlRequest request = context.bindVariables(CrawlRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("url", request.url());
    requestBody.put("maxPages", request.maxPages() != null ? request.maxPages() : DEFAULT_MAX_PAGES);
    requestBody.put("depth", request.depth() != null ? request.depth() : DEFAULT_DEPTH);
    requestBody.put("country", request.country() != null && !request.country().isBlank() ? request.country() : "us");
    requestBody.put("useBrowser", request.useBrowser() != null ? request.useBrowser() : Boolean.FALSE);
    if (request.includePatterns() != null && !request.includePatterns().isEmpty()) {
      requestBody.put("includePatterns", request.includePatterns());
    }
    if (request.excludePatterns() != null && !request.excludePatterns().isEmpty()) {
      requestBody.put("excludePatterns", request.excludePatterns());
    }
    if (request.sessionId() != null && !request.sessionId().isBlank()) {
      requestBody.put("sessionId", request.sessionId());
    }
    if (request.sessionName() != null && !request.sessionName().isBlank()) {
      requestBody.put("sessionName", request.sessionName());
    }

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      Map<String, Object> submitted = client.post("/crawl", requestBody);
      String jobId = AnakinApiClient.requireJobId(submitted);
      Map<String, Object> result = client.pollUntilTerminal("/crawl/" + jobId);

      if ("failed".equals(result.get("status"))) {
        Object error = result.get("error");
        throw new ConnectorException(
            "ANAKIN_JOB_FAILED",
            error != null ? error.toString() : "Anakin crawl job failed with no error detail.");
      }
      return result;
    } catch (AnakinApiException e) {
      LOG.error("Anakin crawl request failed for url={} statusCode={}: {}",
          request.url(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
