package io.anakin.camunda;

import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.MonitorChangesRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets the detected changes for a monitor via Anakin's {@code GET /v1/monitors/:id/changes}.
 * Synchronous -- no job id, no polling. Request/response shapes mirror {@code
 * monitorChangesTool} / {@code AnakinClient.monitorChanges} in {@code
 * anakin-mcp/src/tools/monitor.ts} / {@code anakin-mcp/src/client.ts} exactly.
 *
 * <p>Use {@link MonitorListConnectorFunction} first to find the monitor id.
 */
@OutboundConnector(
    name = "Anakin - Monitor Changes",
    inputVariables = {"apiKey", "monitorId"},
    type = "io.anakin:monitor-changes:1")
@ElementTemplate(
    id = "io.anakin.connector.MonitorChanges.v1",
    name = "Anakin - Monitor Changes",
    version = 1,
    description = "Get the detected changes for a website monitor with Anakin"
        + " (https://anakin.io). Synchronous -- no polling.",
    inputDataClass = MonitorChangesRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Monitor")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class MonitorChangesConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(MonitorChangesConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public MonitorChangesConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  MonitorChangesConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    MonitorChangesRequest request = context.bindVariables(MonitorChangesRequest.class);

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.get("/monitors/" + AnakinApiClient.encodePathSegment(request.monitorId()) + "/changes");
    } catch (AnakinApiException e) {
      LOG.error("Anakin monitors/changes request failed for id={} statusCode={}: {}",
          request.monitorId(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
