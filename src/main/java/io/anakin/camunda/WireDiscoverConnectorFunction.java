package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.WireDiscoverRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds Wire actions for a task from a natural-language intent, via Anakin's {@code GET
 * /v1/wire/resolve?q=&limit=}. Synchronous -- no job id, no polling. Request/response shapes
 * mirror {@code wireDiscoverTool} / {@code AnakinClient.wireResolve} in {@code
 * anakin-mcp/src/tools/wire.ts} / {@code anakin-mcp/src/client.ts} exactly.
 *
 * <p>Wire is Anakin's catalog of pre-built automation actions across hundreds of sites. The
 * intended flow: discover an action here (or with {@link WireCatalogConnectorFunction}), then run
 * it with {@link WireReadActionConnectorFunction} or {@link WireWriteActionConnectorFunction}
 * depending on the action's reported {@code type}.
 */
@OutboundConnector(
    name = "Anakin - Wire Discover",
    inputVariables = {"apiKey", "q", "limit"},
    type = "io.anakin:wire-discover:1")
@ElementTemplate(
    id = "io.anakin.connector.WireDiscover.v1",
    name = "Anakin - Wire Discover",
    version = 1,
    description = "Find Wire actions for a task from a natural-language intent with Anakin"
        + " (https://anakin.io). Synchronous -- no polling.",
    inputDataClass = WireDiscoverRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Discovery input")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class WireDiscoverConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(WireDiscoverConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public WireDiscoverConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  WireDiscoverConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    WireDiscoverRequest request = context.bindVariables(WireDiscoverRequest.class);

    Map<String, String> query = new LinkedHashMap<>();
    query.put("q", request.q());
    query.put("limit", request.limit() != null ? String.valueOf(request.limit()) : null);

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.get(AnakinApiClient.withQuery("/wire/resolve", query));
    } catch (AnakinApiException e) {
      LOG.error("Anakin wire/resolve request failed for q={} statusCode={}: {}",
          request.q(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
