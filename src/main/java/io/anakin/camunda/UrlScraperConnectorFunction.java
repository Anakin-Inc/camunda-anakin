package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.UrlScraperRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scrapes a single URL via Anakin's {@code POST /v1/url-scraper} (submit) then {@code GET
 * /v1/url-scraper/:jobId} (poll) pair.
 *
 * <p>Request/response shapes mirror the official anakin-py SDK ({@code anakin/client.py
 * Anakin.scrape}, {@code anakin/models.py Document}) exactly -- read directly from that source,
 * not inferred.
 *
 * <p>Blocks the job worker thread inside {@link #execute} until the Anakin job reaches a terminal
 * state or {@link AnakinApiClient#POLL_TIMEOUT} elapses -- see {@code SUBMIT.md} for why this,
 * rather than a {@code ConnectorRetryException}-driven re-invoke, is the design choice here.
 */
@OutboundConnector(
    name = "Anakin - Scrape URL",
    inputVariables = {
      "apiKey", "url", "formats", "country", "useBrowser", "generateJson", "forceFresh", "sessionId", "sessionName"
    },
    type = "io.anakin:url-scraper:1")
@ElementTemplate(
    id = "io.anakin.connector.UrlScraper.v1",
    name = "Anakin - Scrape URL",
    version = 1,
    description = "Scrape a single URL with Anakin (https://anakin.io) -- markdown/HTML/JSON"
        + " extraction, optional real-browser rendering for JS-heavy or anti-bot sites.",
    inputDataClass = UrlScraperRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Scrape input"),
      @PropertyGroup(id = "session", label = "Browser session (optional)")
    },
    // Inferred from the SDK version this connector compiles against (8.9.x), not independently
    // confirmed against a published Camunda engine-compatibility matrix -- see SUBMIT.md.
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class UrlScraperConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(UrlScraperConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public UrlScraperConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  UrlScraperConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    UrlScraperRequest request = context.bindVariables(UrlScraperRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("url", request.url());
    requestBody.put("formats", request.formats() != null && !request.formats().isEmpty() ? request.formats() : List.of("markdown"));
    requestBody.put("country", request.country() != null && !request.country().isBlank() ? request.country() : "us");
    requestBody.put("useBrowser", request.useBrowser() != null ? request.useBrowser() : Boolean.FALSE);
    requestBody.put("generateJson", request.generateJson() != null ? request.generateJson() : Boolean.FALSE);
    requestBody.put("forceFresh", request.forceFresh() != null ? request.forceFresh() : Boolean.FALSE);
    if (request.sessionId() != null && !request.sessionId().isBlank()) {
      requestBody.put("sessionId", request.sessionId());
    }
    if (request.sessionName() != null && !request.sessionName().isBlank()) {
      requestBody.put("sessionName", request.sessionName());
    }

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      Map<String, Object> submitted = client.post("/url-scraper", requestBody);
      String jobId = AnakinApiClient.requireJobId(submitted);
      Map<String, Object> result = client.pollUntilTerminal("/url-scraper/" + jobId);

      if ("failed".equals(result.get("status"))) {
        Object error = result.get("error");
        throw new ConnectorException(
            "ANAKIN_JOB_FAILED",
            error != null ? error.toString() : "Anakin url-scraper job failed with no error detail.");
      }
      return result;
    } catch (AnakinApiException e) {
      LOG.error("Anakin url-scraper request failed for url={} statusCode={}: {}",
          request.url(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
