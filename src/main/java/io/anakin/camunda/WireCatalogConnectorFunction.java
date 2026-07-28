package io.anakin.camunda;

import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.WireCatalogRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Browses the Wire catalog via Anakin's {@code GET /v1/wire/catalog} (list every catalog) or
 * {@code GET /v1/wire/catalog/:slug} (one catalog's full action list with param schemas) when a
 * slug is supplied. Synchronous -- no job id, no polling. Request/response shapes mirror {@code
 * wireCatalogTool} / {@code AnakinClient.wireCatalog} in {@code anakin-mcp/src/tools/wire.ts} /
 * {@code anakin-mcp/src/client.ts} exactly.
 */
@OutboundConnector(
    name = "Anakin - Wire Catalog",
    inputVariables = {"apiKey", "slug"},
    type = "io.anakin:wire-catalog:1")
@ElementTemplate(
    id = "io.anakin.connector.WireCatalog.v1",
    name = "Anakin - Wire Catalog",
    version = 1,
    description = "Browse the Wire catalog with Anakin (https://anakin.io) -- list every"
        + " supported site, or one site's full action list. Synchronous -- no polling.",
    inputDataClass = WireCatalogRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Catalog input")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class WireCatalogConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(WireCatalogConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public WireCatalogConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  WireCatalogConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    WireCatalogRequest request = context.bindVariables(WireCatalogRequest.class);

    boolean hasSlug = request.slug() != null && !request.slug().isBlank();
    String path = hasSlug
        ? "/wire/catalog/" + AnakinApiClient.encodePathSegment(request.slug().trim())
        : "/wire/catalog";

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.get(path);
    } catch (AnakinApiException e) {
      LOG.error("Anakin wire/catalog request failed for slug={} statusCode={}: {}",
          request.slug(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
