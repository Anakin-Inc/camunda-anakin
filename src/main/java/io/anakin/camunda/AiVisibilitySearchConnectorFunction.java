package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.AiVisibilitySearchRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks multiple AI answer engines (ChatGPT, Gemini, Google AI Overview, ...) the same question
 * and compares their answers, via Anakin's {@code POST /v1/ai-visibility/search {query,
 * sources?, country?}} (submit) then {@code GET /v1/ai-visibility/search/:search_id} (poll) pair.
 * Request/response shapes mirror {@code aiVisibilitySearchTool} / {@code
 * AnakinClient.aiVisibilitySearch} in {@code anakin-mcp/src/tools/ai-visibility.ts} / {@code
 * anakin-mcp/src/client.ts} exactly.
 *
 * <p>Unlike every other polling connector in this project, a Anakin {@code "failed"} status is
 * <em>not</em> translated into a thrown {@code ConnectorException} here -- per {@code
 * AnakinClient.aiVisibilitySearch}'s own comment, "a failed status is returned (not thrown): the
 * payload carries per-source results and errors the caller needs either way" (some engines can
 * fail while others succeed). This connector always returns the raw response map and lets the
 * BPMN process decide what to do with a partial/failed search from its own {@code status} field.
 *
 * <p>Also unlike the other polling connectors, the terminal-state rule is "any status other than
 * {@code running}", not a fixed literal set -- see {@link
 * AnakinApiClient#pollUntilTerminal(String, java.time.Duration, java.util.function.Predicate)}.
 * Blocks the job worker thread inside {@link #execute} until that happens or {@link
 * AnakinApiClient#POLL_TIMEOUT} elapses -- see {@code SUBMIT.md} for the sync-vs-poll design
 * rationale (same choice as {@link UrlScraperConnectorFunction}).
 */
@OutboundConnector(
    name = "Anakin - AI Visibility Search",
    inputVariables = {"apiKey", "query", "sources", "country", "includeFullContent"},
    type = "io.anakin:ai-visibility-search:1")
@ElementTemplate(
    id = "io.anakin.connector.AiVisibilitySearch.v1",
    name = "Anakin - AI Visibility Search",
    version = 1,
    description = "Compare how multiple AI answer engines respond to the same question, with"
        + " Anakin (https://anakin.io). Transparently polls to completion.",
    inputDataClass = AiVisibilitySearchRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Search input")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class AiVisibilitySearchConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(AiVisibilitySearchConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public AiVisibilitySearchConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  AiVisibilitySearchConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    AiVisibilitySearchRequest request = context.bindVariables(AiVisibilitySearchRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("query", request.query());
    if (request.sources() != null && !request.sources().isEmpty()) {
      requestBody.put("sources", request.sources());
    }
    if (request.country() != null && !request.country().isBlank()) {
      requestBody.put("country", request.country());
    }

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      Map<String, Object> submitted = client.post("/ai-visibility/search", requestBody);
      Object searchId = submitted.get("search_id");
      if (!(searchId instanceof String) || ((String) searchId).isEmpty()) {
        throw new AnakinApiException(
            "Anakin API response did not include a search_id: " + submitted, (Throwable) null);
      }
      Map<String, Object> search = client.pollUntilTerminal(
          "/ai-visibility/search/" + AnakinApiClient.encodePathSegment((String) searchId),
          AnakinApiClient.POLL_TIMEOUT,
          status -> !"running".equals(status));

      boolean includeFull = Boolean.TRUE.equals(request.includeFullContent());
      return includeFull ? search : withoutFullContent(search);
    } catch (AnakinApiException e) {
      LOG.error("Anakin ai-visibility/search request failed for query={} statusCode={}: {}",
          request.query(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }

  /** Strips each per-source result's {@code full_content} field (large) unless the caller opted
   * in via {@code includeFullContent} -- matches {@code aiVisibilitySearchTool}'s handler in
   * {@code anakin-mcp/src/tools/ai-visibility.ts}, which drops it by default. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> withoutFullContent(Map<String, Object> search) {
    Object results = search.get("results");
    if (!(results instanceof List)) {
      return search;
    }
    List<Object> stripped = new java.util.ArrayList<>();
    for (Object entry : (List<Object>) results) {
      if (entry instanceof Map) {
        Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) entry);
        copy.remove("full_content");
        stripped.add(copy);
      } else {
        stripped.add(entry);
      }
    }
    Map<String, Object> copy = new LinkedHashMap<>(search);
    copy.put("results", stripped);
    return copy;
  }
}
