package io.anakin.camunda;

import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.MonitorControlRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls an existing website monitor via Anakin's {@code POST /v1/monitors/:id/pause}, {@code
 * POST /v1/monitors/:id/resume}, {@code POST /v1/monitors/:id/run}, or {@code DELETE
 * /v1/monitors/:id} depending on {@code action}. Synchronous -- no job id, no polling.
 * Request/response shapes mirror {@code monitorControlTool} / {@code AnakinClient.monitorControl}
 * in {@code anakin-mcp/src/tools/monitor.ts} / {@code anakin-mcp/src/client.ts} exactly (note
 * that {@code client.ts}'s {@code action} parameter values are {@code pause}/{@code
 * resume}/{@code run_now}/{@code delete}, but only {@code run_now} maps to a differently-named
 * path segment, {@code /run} -- reproduced verbatim from that switch statement, not guessed).
 *
 * <p>Use {@link MonitorListConnectorFunction} first to find the monitor id.
 */
@OutboundConnector(
    name = "Anakin - Control Monitor",
    inputVariables = {"apiKey", "monitorId", "action"},
    type = "io.anakin:monitor-control:1")
@ElementTemplate(
    id = "io.anakin.connector.MonitorControl.v1",
    name = "Anakin - Control Monitor",
    version = 1,
    description = "Pause, resume, run now, or permanently delete a website monitor with Anakin"
        + " (https://anakin.io). Synchronous -- no polling.",
    inputDataClass = MonitorControlRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Monitor")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class MonitorControlConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(MonitorControlConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public MonitorControlConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  MonitorControlConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    MonitorControlRequest request = context.bindVariables(MonitorControlRequest.class);

    String action = request.action();
    String base = "/monitors/" + AnakinApiClient.encodePathSegment(request.monitorId());

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      Map<String, Object> emptyBody = Map.of();
      return switch (action) {
        case "pause" -> client.post(base + "/pause", emptyBody);
        case "resume" -> client.post(base + "/resume", emptyBody);
        case "run_now" -> client.post(base + "/run", emptyBody);
        case "delete" -> client.delete(base);
        default -> throw new ConnectorException(
            "ANAKIN_INVALID_INPUT",
            "Unknown monitor action \"" + action + "\" -- use pause, resume, run_now, or delete.");
      };
    } catch (AnakinApiException e) {
      LOG.error("Anakin monitors/control request failed for id={} action={} statusCode={}: {}",
          request.monitorId(), action, e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
