package io.anakin.camunda;

import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.SessionDeleteRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Permanently deletes a saved browser session via Anakin's {@code DELETE /v1/sessions/:id}.
 * Synchronous -- no job id, no polling. Irreversible: the encrypted login data is destroyed, and
 * any monitors or requests referencing this session id lose authenticated access. Request/
 * response shapes mirror {@code sessionDeleteTool} / {@code AnakinClient.sessionDelete} in {@code
 * anakin-mcp/src/tools/sessions.ts} / {@code anakin-mcp/src/client.ts} exactly.
 *
 * <p>Use {@link SessionListConnectorFunction} first to find the session id.
 */
@OutboundConnector(
    name = "Anakin - Delete Session",
    inputVariables = {"apiKey", "sessionId"},
    type = "io.anakin:session-delete:1")
@ElementTemplate(
    id = "io.anakin.connector.SessionDelete.v1",
    name = "Anakin - Delete Session",
    version = 1,
    description = "Permanently delete a saved browser session with Anakin (https://anakin.io)."
        + " Irreversible. Synchronous -- no polling.",
    inputDataClass = SessionDeleteRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Session")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class SessionDeleteConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(SessionDeleteConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public SessionDeleteConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  SessionDeleteConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    SessionDeleteRequest request = context.bindVariables(SessionDeleteRequest.class);

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.delete("/sessions/" + AnakinApiClient.encodePathSegment(request.sessionId()));
    } catch (AnakinApiException e) {
      LOG.error("Anakin sessions (delete) request failed for id={} statusCode={}: {}",
          request.sessionId(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
