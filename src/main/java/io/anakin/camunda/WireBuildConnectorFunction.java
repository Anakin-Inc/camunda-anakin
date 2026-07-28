package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.WireBuildRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Requests a brand-new Wire action for a website not yet in the catalog, via Anakin's {@code POST
 * /v1/wire/build-request {website_url, goal, catalog_id?, visibility?, force?}}. Asynchronous
 * server-side (returns status {@code "pending"}, charges credits, refunded automatically on
 * build failure) but this connector does not itself poll a job-status endpoint -- there is no
 * {@code GET /wire/build-request/:id} in the API surface this project was given; the submit
 * response's own {@code status: "pending"} is the terminal result of this call. Request/response
 * shapes mirror {@code wireBuildTool} / {@code AnakinClient.wireBuild} in {@code
 * anakin-mcp/src/tools/wire.ts} / {@code anakin-mcp/src/client.ts} exactly.
 *
 * <p>Only use after {@link WireDiscoverConnectorFunction} / {@link WireCatalogConnectorFunction}
 * confirm no existing action covers the site.
 */
@OutboundConnector(
    name = "Anakin - Wire Build",
    inputVariables = {"apiKey", "websiteUrl", "goal", "catalogId", "visibility", "force"},
    type = "io.anakin:wire-build:1")
@ElementTemplate(
    id = "io.anakin.connector.WireBuild.v1",
    name = "Anakin - Wire Build",
    version = 1,
    description = "Request a brand-new Wire action for an uncatalogued site with Anakin"
        + " (https://anakin.io). Returns status \"pending\" -- Wire builds and publishes the"
        + " action asynchronously.",
    inputDataClass = WireBuildRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Build input")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class WireBuildConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(WireBuildConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public WireBuildConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  WireBuildConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    WireBuildRequest request = context.bindVariables(WireBuildRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("website_url", request.websiteUrl());
    requestBody.put("goal", request.goal());
    if (request.catalogId() != null && !request.catalogId().isBlank()) {
      requestBody.put("catalog_id", request.catalogId());
    }
    if (request.visibility() != null && !request.visibility().isBlank()) {
      requestBody.put("visibility", request.visibility());
    }
    requestBody.put("force", request.force() != null ? request.force() : Boolean.FALSE);

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      return client.post("/wire/build-request", requestBody);
    } catch (AnakinApiException e) {
      LOG.error("Anakin wire/build-request request failed for websiteUrl={} statusCode={}: {}",
          request.websiteUrl(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
