package io.anakin.camunda;

import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.MonitorListRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists website monitors via Anakin's {@code GET /v1/monitors}, or fetches one monitor's full
 * configuration via {@code GET /v1/monitors/:id} when {@code id} is supplied. Synchronous -- no
 * job id, no polling. Request/response shapes mirror {@code monitorListTool} / {@code
 * AnakinClient.monitorList} / {@code monitorGet} in {@code anakin-mcp/src/tools/monitor.ts} /
 * {@code anakin-mcp/src/client.ts} exactly.
 */
@OutboundConnector(
    name = "Anakin - List Monitors",
    inputVariables = {"apiKey", "monitorId"},
    type = "io.anakin:monitor-list:1")
@ElementTemplate(
    id = "io.anakin.connector.MonitorList.v1",
    name = "Anakin - List Monitors",
    version = 1,
    description = "List website monitors, or fetch one by ID, with Anakin (https://anakin.io)."
        + " Synchronous -- no polling.",
    inputDataClass = MonitorListRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Filter")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class MonitorListConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(MonitorListConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public MonitorListConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  MonitorListConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    MonitorListRequest request = context.bindVariables(MonitorListRequest.class);

    boolean hasId = request.monitorId() != null && !request.monitorId().isBlank();
    String path = hasId
        ? "/monitors/" + AnakinApiClient.encodePathSegment(request.monitorId().trim())
        : "/monitors";

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.get(path);
    } catch (AnakinApiException e) {
      LOG.error("Anakin monitors (list) request failed for id={} statusCode={}: {}",
          request.monitorId(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
