package io.anakin.camunda;

import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.AiVisibilitySourcesRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists the AI answer engines available to {@link AiVisibilitySearchConnectorFunction}, via
 * Anakin's {@code GET /v1/ai-visibility/sources}. Synchronous -- no job id, no polling, no input
 * beyond authentication. Request/response shapes mirror {@code aiVisibilitySourcesTool} / {@code
 * AnakinClient.aiVisibilitySources} in {@code anakin-mcp/src/tools/ai-visibility.ts} / {@code
 * anakin-mcp/src/client.ts} exactly.
 */
@OutboundConnector(
    name = "Anakin - AI Visibility Sources",
    inputVariables = {"apiKey"},
    type = "io.anakin:ai-visibility-sources:1")
@ElementTemplate(
    id = "io.anakin.connector.AiVisibilitySources.v1",
    name = "Anakin - AI Visibility Sources",
    version = 1,
    description = "List the AI answer engines available to AI Visibility Search, with Anakin"
        + " (https://anakin.io). Synchronous -- no polling.",
    inputDataClass = AiVisibilitySourcesRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class AiVisibilitySourcesConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(AiVisibilitySourcesConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public AiVisibilitySourcesConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  AiVisibilitySourcesConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    AiVisibilitySourcesRequest request = context.bindVariables(AiVisibilitySourcesRequest.class);

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.get("/ai-visibility/sources");
    } catch (AnakinApiException e) {
      LOG.error("Anakin ai-visibility/sources request failed statusCode={}: {}",
          e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
