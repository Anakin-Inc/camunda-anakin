package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.SearchRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI-powered web search via Anakin's {@code POST /v1/search}.
 *
 * <p>Synchronous -- unlike the other two connectors in this project, there is no job id and no
 * polling; the API returns results directly within a single request. Costs 3 credits per call
 * (see anakin-py's {@code Anakin.search} docstring).
 */
@OutboundConnector(
    name = "Anakin - AI Search",
    inputVariables = {"apiKey", "prompt", "limit"},
    type = "io.anakin:search:1")
@ElementTemplate(
    id = "io.anakin.connector.Search.v1",
    name = "Anakin - AI Search",
    version = 1,
    description = "AI-powered web search with Anakin (https://anakin.io). Synchronous -- no"
        + " polling.",
    inputDataClass = SearchRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Search input")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class SearchConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(SearchConnectorFunction.class);

  private static final int DEFAULT_LIMIT = 5;

  private final Function<String, AnakinApiClient> clientFactory;

  public SearchConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  SearchConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    SearchRequest request = context.bindVariables(SearchRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("prompt", request.prompt());
    requestBody.put("limit", request.limit() != null ? request.limit() : DEFAULT_LIMIT);

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.post("/search", requestBody);
    } catch (AnakinApiException e) {
      LOG.error("Anakin search request failed for prompt={} statusCode={}: {}",
          request.prompt(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
