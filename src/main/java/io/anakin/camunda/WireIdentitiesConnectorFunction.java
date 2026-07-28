package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.WireIdentitiesRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists saved Wire identities and their credentials via Anakin's {@code GET
 * /v1/wire/identities?catalog_id=}. Synchronous -- no job id, no polling. Request/response shapes
 * mirror {@code wireIdentitiesTool} / {@code AnakinClient.wireIdentities} in {@code
 * anakin-mcp/src/tools/wire.ts} / {@code anakin-mcp/src/client.ts} exactly.
 *
 * <p>Each returned credential's {@code id} is the {@code credentialId} to pass to {@link
 * WireReadActionConnectorFunction} / {@link WireWriteActionConnectorFunction} for actions whose
 * {@code auth_mode} is {@code "required"}.
 */
@OutboundConnector(
    name = "Anakin - Wire Identities",
    inputVariables = {"apiKey", "catalogId"},
    type = "io.anakin:wire-identities:1")
@ElementTemplate(
    id = "io.anakin.connector.WireIdentities.v1",
    name = "Anakin - Wire Identities",
    version = 1,
    description = "List saved Wire identities and credentials with Anakin (https://anakin.io)."
        + " Synchronous -- no polling.",
    inputDataClass = WireIdentitiesRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Filter")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class WireIdentitiesConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(WireIdentitiesConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public WireIdentitiesConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  WireIdentitiesConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    WireIdentitiesRequest request = context.bindVariables(WireIdentitiesRequest.class);

    Map<String, String> query = new LinkedHashMap<>();
    query.put("catalog_id", request.catalogId());

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.get(AnakinApiClient.withQuery("/wire/identities", query));
    } catch (AnakinApiException e) {
      LOG.error("Anakin wire/identities request failed for catalogId={} statusCode={}: {}",
          request.catalogId(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
